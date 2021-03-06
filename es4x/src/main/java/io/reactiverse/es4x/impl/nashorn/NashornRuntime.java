/*
 * Copyright 2018 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.reactiverse.es4x.impl.nashorn;

import io.reactiverse.es4x.Runtime;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.NashornScriptEngine;

import javax.script.*;

public class NashornRuntime implements Runtime<Object> {

  private final NashornScriptEngine engine;
  private final JSObject require;
  private final Object module;

  public NashornRuntime(final Vertx vertx, NashornScriptEngine engine) {
    this.engine = engine;
    try {
      final Bindings engineBindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
      // remove the exit and quit functions
      engineBindings.remove("exit");
      engineBindings.remove("quit");

      final Bindings globalBindings = new SimpleBindings();
      // add vertx as a global
      globalBindings.put("vertx", vertx);
      // add the global reference to the bindings
      globalBindings.put("global", engine.eval("this"));

      engine.setBindings(globalBindings, ScriptContext.GLOBAL_SCOPE);

      // add polyfills
      engine.invokeFunction("load", "classpath:io/reactiverse/es4x/polyfill/object.js");
      engine.invokeFunction("load", "classpath:io/reactiverse/es4x/polyfill/json.js");
      engine.invokeFunction("load", "classpath:io/reactiverse/es4x/polyfill/global.js");
      engine.invokeFunction("load", "classpath:io/reactiverse/es4x/polyfill/date.js");
      engine.invokeFunction("load", "classpath:io/reactiverse/es4x/polyfill/console.js");
      engine.invokeFunction("load", "classpath:io/reactiverse/es4x/polyfill/promise.js");
      engine.invokeFunction("load", "classpath:io/reactiverse/es4x/polyfill/worker.js");
      // install the commonjs loader
      module = engine.invokeFunction("load", "classpath:io/reactiverse/es4x/jvm-npm.js");
      // get a reference to the require function
      require = (JSObject) engine.get("require");

    } catch (ScriptException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void config(final JsonObject config) {
    final Bindings engineBindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
    // expose the config as a global
    engineBindings.put("config", config != null ? config.getMap() : null);
  }

  @Override
  public Object require(String main) {
    return require.call(null, main);
  }

  @Override
  public Object main(String main) {
    if (main.equals(".") || main.equals("..")) {
      // invoke the main script
      return invokeMethod(module, "runMain", main + "/");
    }
    // patch the main path to be a relative path
    if (!main.startsWith("./") && !main.startsWith("/")) {
      main = "./" + main;
    }
    // invoke the main script
    return invokeMethod(module, "runMain", main);
  }

  @Override
  public Object worker(String main, String address) {
    // invoke the main script
    return invokeMethod(module, "runWorker", main, address);
  }

  @Override
  public Object eval(String script, String name, String contentType, boolean literal) throws ScriptException {
    return engine.eval(script);
  }

  @Override
  public boolean hasMember(Object thiz, String key) {
    if (thiz instanceof JSObject) {
      return (((JSObject) thiz).hasMember(key));
    }
    return false;
  }

  @Override
  public Object invokeMethod(Object thiz, String method, Object... args) {
    if (thiz instanceof JSObject) {
      if (((JSObject) thiz).hasMember(method)) {
        Object fn = ((JSObject) thiz).getMember(method);
        return ((JSObject) fn).call(thiz, args);
      }
    }

    return null;
  }

  @Override
  public Object invokeFunction(String function, Object... args) {
    try {
      return engine.invokeFunction(function, args);
    } catch (ScriptException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void put(String name, Object value) {
    engine.put(name, value);
  }

  @Override
  public void close() {
    // NO-OP
  }
}
