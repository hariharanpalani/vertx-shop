package com.ecit.service;

import io.vertx.codegen.annotations.*;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * Created by za-wangshenhua on 2018/2/2.
 */
@ProxyGen
@VertxGen
public interface IUserService {

    public static final String USER_SERVICE_ADDRESS = "user-service-address";

    @Fluent
    IUserService register(String loginName, String mobile, String email, String pwd, String salt, Handler<AsyncResult<Integer>> resultHandler);

    @Fluent
    IUserService login(String loginName, String pwd, Handler<AsyncResult<JsonObject>> resultHandler);

    @Fluent
    IUserService changePwd(long userId, String pwd, long versions, Handler<AsyncResult<Integer>> resultHandler);

    @Fluent
    IUserService getMemberById(long userId, Handler<AsyncResult<JsonObject>> resultHandler);

    @Fluent
    IUserService activateEmailUser(long userId, long versions, Handler<AsyncResult<Integer>> resultHandler);

    @Fluent
    IUserService findEmailUser(String loginName, Handler<AsyncResult<JsonObject>> resultHandler);
}