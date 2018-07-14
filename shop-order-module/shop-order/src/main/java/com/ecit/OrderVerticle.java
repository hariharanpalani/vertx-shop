package com.ecit;

import com.ecit.api.RestCartRxVerticle;
import com.ecit.api.RestOrderRxVerticle;
import com.ecit.common.rx.BaseMicroserviceRxVerticle;
import com.ecit.service.ICartService;
import com.ecit.service.IOrderService;
import com.ecit.service.impl.CartServiceImpl;
import com.ecit.service.impl.OrderServiceImpl;
import com.hazelcast.config.Config;
import com.hazelcast.config.GroupConfig;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.reactivex.core.Vertx;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

/**
 * Created by za-wangshenhua on 2018/2/2.
 */
public class OrderVerticle extends BaseMicroserviceRxVerticle{

    private static final String ORDER_SERVICE_NAME = "order_service_name";
    private static final String CART_SERVICE_NAME = "cart_service_name";

    @Override
    public void start() throws Exception {
        super.start();
        IOrderService orderService = new OrderServiceImpl(vertx, this.config());
        ICartService cartService = new CartServiceImpl(vertx, this.config());
        new ServiceBinder(vertx.getDelegate())
                .setAddress(IOrderService.ORDER_SERVICE_ADDRESS)
                .register(IOrderService.class, orderService);
        new ServiceBinder(vertx.getDelegate())
                .setAddress(ICartService.CART_SERVICE_ADDRESS)
                .register(ICartService.class, cartService);
        this.publishEventBusService(ORDER_SERVICE_NAME, IOrderService.ORDER_SERVICE_ADDRESS, IOrderService.class).subscribe();
        this.publishEventBusService(CART_SERVICE_NAME, ICartService.CART_SERVICE_ADDRESS, ICartService.class).subscribe();
        vertx.getDelegate().deployVerticle(new RestOrderRxVerticle(orderService), new DeploymentOptions().setConfig(this.config()));
        vertx.getDelegate().deployVerticle(new RestCartRxVerticle(cartService), new DeploymentOptions().setConfig(this.config()));
    }

    public static void main(String[] args) {
        Config cfg = new Config();
        GroupConfig group = new GroupConfig();
        group.setName("p-dev");
        group.setPassword("p-dev");
        cfg.setGroupConfig(group);
        // 申明集群管理器
        ClusterManager mgr = new HazelcastClusterManager(cfg);
        VertxOptions options = new VertxOptions().setClusterManager(mgr);
        Vertx.rxClusteredVertx(options).subscribe(v -> {
            v.deployVerticle(OrderVerticle.class.getName(),
                    new DeploymentOptions().setConfig(new JsonObject()
                            .put("order.api.name", "order")
                            .put("cart.api.name", "cart")
                            .put("host", "111.231.132.168")
                            .put("port", 5432)
                            .put("maxPoolSize", 50)
                            .put("username", "postgres")
                            .put("password", "h123456")
                            .put("database", "vertx_shop")
                            .put("charset", "UTF-8")
                            .put("queryTimeout", 10000)
                            .put("mongodb", new JsonObject()
                                    .put("host", "111.231.132.168")
                                    .put("port", 27017)
                                    .put("username", "shop_user")
                                    .put("password", "h123456")
                                    .put("db_name", "vertx_shop"))
                    ));
        });
    }
}