package com.ecit.api;

import com.ecit.common.enmu.RegisterType;
import com.ecit.common.rx.RestAPIRxVerticle;
import com.ecit.service.IMessageService;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Created by za-wangshenhua on 2018/2/2.
 */
public class RestMessageRxVerticle extends RestAPIRxVerticle{

    private static final Logger LOGGER = LogManager.getLogger(RestMessageRxVerticle.class);
    private static final String HTTP_MESSAGE_SERVICE = "http_message_service_api";
    private final IMessageService messageService;

    public RestMessageRxVerticle(IMessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void start() throws Exception {
        super.start();
        final Router router = Router.router(vertx);
        // body handler
        router.route().handler(BodyHandler.create());
        // API route handler
        router.post("/insertMessage").handler(this::insertMessageHandler);
        //router.post("/findMessage").handler(this::findMessageHandler);
        //全局异常处理
        this.globalVerticle(router);

        // get HTTP host and port from configuration, or use default value
        String host = config().getString("api.message.http.address", "localhost");
        int port = config().getInteger("api.message.http.port", 8081);

        // create HTTP server and publish REST service
        createHttpServer(router, host, port).subscribe(server -> {
            this.publishHttpEndpoint(HTTP_MESSAGE_SERVICE, host, port).subscribe();
            LOGGER.info("shop-message server started!");
        }, error -> {
            LOGGER.info("shop-message server start fail!", error);
        });
    }

    /**
     *
     * @param context
     */
    private void insertMessageHandler(RoutingContext context){
        HttpServerRequest request = context.request();
        messageService.saveMessage(request.getParam("destination"), RegisterType.mobile, handler ->{
            if(handler.succeeded()){
                LOGGER.info("插入成功，code:{}", handler.result());
                this.returnWithSuccessMessage(context, "插入成功");
            } else {
                LOGGER.info("插入失败", handler.cause());
                this.returnWithFailureMessage(context, "插入失败");
            }
        });
    }

    /*private void findMessageHandler(RoutingContext context){
        HttpServerRequest request = context.request();
        messageService.findMessage(request.getParam("destination"), RegisterType.mobile, handler ->{
            if(handler.succeeded()){
                messageService.updateMessage(request.getParam("destination"), RegisterType.mobile, deleteHandler -> {
                    if (deleteHandler.succeeded()) {
                        LOGGER.info("数据删除成功！");
                    } else {
                        LOGGER.info("数据删除失败！", deleteHandler.cause());
                    }
                });
                this.Ok(context, ResultItems.getReturnItemsSuccess(0, handler.result()));
                //this.returnWithSuccessMessage(context, "插入成功");
            } else {
                LOGGER.info("查询失败", handler.cause());
                this.returnWithFailureMessage(context, "查询失败");
            }
        });
    }*/

}