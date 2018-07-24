package com.ecit.service.impl;

import com.ecit.common.db.JdbcRxRepositoryWrapper;
import com.ecit.common.enums.RegisterType;
import com.ecit.common.utils.salt.DefaultHashStrategy;
import com.ecit.common.utils.salt.ShopHashStrategy;
import com.ecit.constants.AccountSql;
import com.ecit.enmu.CertifiedType;
import com.ecit.enmu.PayStatus;
import com.ecit.enmu.PayType;
import com.ecit.enums.OrderStatus;
import com.ecit.service.*;
import io.reactivex.Single;
import io.reactivex.exceptions.CompositeException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.serviceproxy.ServiceProxyBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.math.BigDecimal;
import java.util.Objects;

public class AccountServiceImpl extends JdbcRxRepositoryWrapper implements IAccountService {

    private static final Logger LOGGER = LogManager.getLogger(AccountServiceImpl.class);
    static Vertx vertx;
    private static ShopHashStrategy hashStrategy;

    public AccountServiceImpl(Vertx vertx, JsonObject config) {
        super(vertx, config);
        this.vertx = vertx;
        this.hashStrategy = new DefaultHashStrategy(vertx.getDelegate());
    }

    /**
     * 初始化用户资金账户
     * @param userId
     * @param handler
     * @return
     */
    @Override
    public IAccountService insertAccount(long userId, Handler<AsyncResult<Integer>> handler) {
        Future<Integer> future = Future.future();
        this.execute(new JsonArray().add(IdBuilder.getUniqueId()).add(userId), AccountSql.INSERT_ACCOUNT_SQL)
                .subscribe(future::complete, future::fail);
        future.setHandler(handler);
        return this;
    }

    /**
     * 根据用户id查询资金账户
     * @param userId
     * @param handler
     * @return
     */
    @Override
    public IAccountService findAccount(long userId, Handler<AsyncResult<JsonObject>> handler) {
        Future<JsonObject> future = Future.future();
        this.retrieveOne(new JsonArray().add(userId), AccountSql.FIND_ACCOUNT_BY_USERID_SQL)
                .subscribe(future::complete, future::fail);
        future.setHandler(handler);
        return this;
    }

    /**
     * 订单支付（账户余额）
     * @param userId
     * @param orderId
     * @param payPwd
     * @param handler
     * @return
     */
    @Override
    public IAccountService payOrder(long userId, long orderId, String payPwd, Handler<AsyncResult<Integer>> handler) {
        IOrderService orderService = new ServiceProxyBuilder(vertx.getDelegate())
                .setAddress(IOrderService.ORDER_SERVICE_ADDRESS).build(IOrderService.class);
        Future<JsonObject> orderFuture = Future.future();
        orderService.getOrderById(orderId, userId, orderFuture);
        orderFuture.compose(order -> {
            //检查库存；检查账户余额；扣款；
            if(Objects.isNull(order)){
                LOGGER.error("用户【{}】支付订单【{}】不存在！", userId, orderId);
                return Future.failedFuture("订单不存在！");
            }
            if(OrderStatus.VALID.getValue() != order.getInteger("order_status").intValue()){
                LOGGER.error("订单【{}】状态【{}】不正确！", orderId, order.getInteger("order_status"));
                return Future.failedFuture("订单状态不正确！");
            }
            Future<JsonObject> accountFuture = Future.future();
            this.findAccount(userId, accountFuture);
            Future updateAccountFuture = accountFuture.compose(account -> {
                if(Objects.isNull(account)){
                    LOGGER.error("账户信息不存在！");
                    return Future.failedFuture("账户信息异常！");
                }
                if(StringUtils.isEmpty(account.getString("pay_pwd"))){
                    LOGGER.error("未设置支付密码！");
                    return Future.failedFuture("not_pay_pwd");
                }
                //验证密码
                final String pwd = this.hashStrategy.computeHash(payPwd, account.getInteger("account_id").toString(), -1);
                if(!StringUtils.equals(account.getString("pay_pwd"), pwd)){
                    LOGGER.error("支付密码不正确！");
                    return Future.failedFuture("支付密码不正确！");
                }
                
                final String totalPrice = order.getString("total_price");
                if(new BigDecimal(account.getString("amount")).compareTo(new BigDecimal(totalPrice)) < 0){
                    return Future.failedFuture("账户余额不足！");
                }
                Future updateFuture = Future.future();
                postgreSQLClient.rxGetConnection().flatMap(conn ->
                    conn.rxSetAutoCommit(false).toSingleDefault(false)
                    .flatMap(autoCommit -> conn.rxUpdateWithParams(AccountSql.LESS_ACCOUNT_SQL,
                            new JsonArray().add(totalPrice).add(totalPrice).add(userId).add(account.getInteger("versions"))))
                        .flatMap(updateResult -> conn.rxUpdateWithParams(AccountSql.INSERT_PAY_LOG_SQL,
                                new JsonArray().add(IdBuilder.getUniqueId()).add(userId).add(orderId).add(PayType.ACCOUNT.getKey())
                        .add(totalPrice).add(new BigDecimal(account.getString("amount")).subtract(new BigDecimal(totalPrice)).toString())
                                        .add(PayStatus.FINISHED.getKey())))
                            // Rollback if any failed with exception propagation
                            .onErrorResumeNext(ex -> conn.rxRollback()
                                    .toSingleDefault(true)
                                    .onErrorResumeNext(ex2 -> Single.error(new CompositeException(ex, ex2)))
                                    .flatMap(ignore -> Single.error(ex))
                            )
                            // close the connection regardless succeeded or failed
                            .doAfterTerminate(conn::close)
                ).subscribe(updateFuture::complete, updateFuture::fail);
                return updateFuture;
            });
            return updateAccountFuture;
        }).compose(updateAccount -> {
            //更改订单状态
            JsonObject orderJson = orderFuture.result();
            Future<Integer> future = Future.future();
            orderService.payOrder(orderId, orderJson.getInteger("versions"), future);
            return future;
        }).setHandler(handler);
        return this;
    }

    @Override
    public IAccountService changePayPwd(long userId, String payPwd, String code, Handler<AsyncResult<Integer>> handler) {
        IUserService userService = new ServiceProxyBuilder(vertx.getDelegate())
                .setAddress(IUserService.USER_SERVICE_ADDRESS).build(IUserService.class);
        Future<JsonObject> userFuture = Future.future();
        userService.getMemberById(userId, userFuture);
        userFuture.compose(user -> {
            if(Objects.isNull(user) || user.isEmpty()){
                return Future.failedFuture("用户不存在！");
            }
            final String mobile = user.getString("mobile");
            IMessageService messageService = new ServiceProxyBuilder(vertx.getDelegate())
                    .setAddress(IMessageService.MESSAGE_SERVICE_ADDRESS).build(IMessageService.class);
            Future<JsonObject> messageFuture = Future.future();
            messageService.findMessage(mobile, RegisterType.mobile, messageFuture);
            return messageFuture.compose(message ->{
                if(Objects.isNull(message) || message.isEmpty()){
                    return Future.failedFuture("手机验证码不存在！");
                }
                if (!StringUtils.equals(message.getString("code"), code)) {
                    return Future.failedFuture("手机验证码不正确！");
                }
                Future<JsonObject> accountFuture = Future.future();
                this.findAccount(userId, accountFuture);
                return accountFuture;
            }).compose(account -> {
                if(Objects.isNull(account) || account.isEmpty()){
                    return Future.failedFuture("账户【" + userId + "】不存在！");
                }
                final String pwd = this.hashStrategy.computeHash(payPwd, account.getInteger("account_id").toString(), -1);
                Future<Integer> future = Future.future();
                this.execute(new JsonArray().add(pwd).add(userId).add(account.getInteger("versions")), AccountSql.CHANGE_PAY_PWD_SQL)
                        .subscribe(future::complete, future::fail);
                ICertifiedService certifiedService = new ServiceProxyBuilder(vertx.getDelegate())
                        .setAddress(ICertifiedService.CERTIFIED_SERVICE_ADDRESS).build(ICertifiedService.class);
                certifiedService.sendUserCertified(userId, CertifiedType.PAY_CERTIFIED.getKey(), mobile, certifiedHandler -> {});
                messageService.updateMessage(mobile, RegisterType.mobile, messageHandler -> {});
                return future;
            });
        }).setHandler(handler);
        return this;
    }
}
