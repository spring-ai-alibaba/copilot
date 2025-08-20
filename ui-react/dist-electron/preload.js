"use strict";
const require$$0 = require("electron");
var commonjsGlobal = typeof globalThis !== "undefined" ? globalThis : typeof window !== "undefined" ? window : typeof global !== "undefined" ? global : typeof self !== "undefined" ? self : {};
var renderer$1 = {};
var remote = {};
var callbacksRegistry = {};
Object.defineProperty(callbacksRegistry, "__esModule", { value: true });
callbacksRegistry.CallbacksRegistry = void 0;
class CallbacksRegistry {
  constructor() {
    this.nextId = 0;
    this.callbacks = {};
    this.callbackIds = /* @__PURE__ */ new WeakMap();
    this.locationInfo = /* @__PURE__ */ new WeakMap();
  }
  add(callback) {
    let id = this.callbackIds.get(callback);
    if (id != null)
      return id;
    id = this.nextId += 1;
    this.callbacks[id] = callback;
    this.callbackIds.set(callback, id);
    const regexp = /at (.*)/gi;
    const stackString = new Error().stack;
    if (!stackString)
      return id;
    let filenameAndLine;
    let match;
    while ((match = regexp.exec(stackString)) !== null) {
      const location = match[1];
      if (location.includes("(native)"))
        continue;
      if (location.includes("(<anonymous>)"))
        continue;
      if (location.includes("callbacks-registry.js"))
        continue;
      if (location.includes("remote.js"))
        continue;
      if (location.includes("@electron/remote/dist"))
        continue;
      const ref = /([^/^)]*)\)?$/gi.exec(location);
      if (ref)
        filenameAndLine = ref[1];
      break;
    }
    this.locationInfo.set(callback, filenameAndLine);
    return id;
  }
  get(id) {
    return this.callbacks[id] || function() {
    };
  }
  getLocation(callback) {
    return this.locationInfo.get(callback);
  }
  apply(id, ...args) {
    return this.get(id).apply(commonjsGlobal, ...args);
  }
  remove(id) {
    const callback = this.callbacks[id];
    if (callback) {
      this.callbackIds.delete(callback);
      delete this.callbacks[id];
    }
  }
}
callbacksRegistry.CallbacksRegistry = CallbacksRegistry;
var typeUtils = {};
Object.defineProperty(typeUtils, "__esModule", { value: true });
typeUtils.deserialize = typeUtils.serialize = typeUtils.isSerializableObject = typeUtils.isPromise = void 0;
const electron_1 = require$$0;
function isPromise(val) {
  return val && val.then && val.then instanceof Function && val.constructor && val.constructor.reject && val.constructor.reject instanceof Function && val.constructor.resolve && val.constructor.resolve instanceof Function;
}
typeUtils.isPromise = isPromise;
const serializableTypes = [
  Boolean,
  Number,
  String,
  Date,
  Error,
  RegExp,
  ArrayBuffer
];
function isSerializableObject(value) {
  return value === null || ArrayBuffer.isView(value) || serializableTypes.some((type) => value instanceof type);
}
typeUtils.isSerializableObject = isSerializableObject;
const objectMap = function(source, mapper) {
  const sourceEntries = Object.entries(source);
  const targetEntries = sourceEntries.map(([key, val]) => [key, mapper(val)]);
  return Object.fromEntries(targetEntries);
};
function serializeNativeImage(image) {
  const representations = [];
  const scaleFactors = image.getScaleFactors();
  if (scaleFactors.length === 1) {
    const scaleFactor = scaleFactors[0];
    const size = image.getSize(scaleFactor);
    const buffer = image.toBitmap({ scaleFactor });
    representations.push({ scaleFactor, size, buffer });
  } else {
    for (const scaleFactor of scaleFactors) {
      const size = image.getSize(scaleFactor);
      const dataURL = image.toDataURL({ scaleFactor });
      representations.push({ scaleFactor, size, dataURL });
    }
  }
  return { __ELECTRON_SERIALIZED_NativeImage__: true, representations };
}
function deserializeNativeImage(value) {
  const image = electron_1.nativeImage.createEmpty();
  if (value.representations.length === 1) {
    const { buffer, size, scaleFactor } = value.representations[0];
    const { width, height } = size;
    image.addRepresentation({ buffer, scaleFactor, width, height });
  } else {
    for (const rep of value.representations) {
      const { dataURL, size, scaleFactor } = rep;
      const { width, height } = size;
      image.addRepresentation({ dataURL, scaleFactor, width, height });
    }
  }
  return image;
}
function serialize(value) {
  if (value && value.constructor && value.constructor.name === "NativeImage") {
    return serializeNativeImage(value);
  }
  if (Array.isArray(value)) {
    return value.map(serialize);
  } else if (isSerializableObject(value)) {
    return value;
  } else if (value instanceof Object) {
    return objectMap(value, serialize);
  } else {
    return value;
  }
}
typeUtils.serialize = serialize;
function deserialize(value) {
  if (value && value.__ELECTRON_SERIALIZED_NativeImage__) {
    return deserializeNativeImage(value);
  } else if (Array.isArray(value)) {
    return value.map(deserialize);
  } else if (isSerializableObject(value)) {
    return value;
  } else if (value instanceof Object) {
    return objectMap(value, deserialize);
  } else {
    return value;
  }
}
typeUtils.deserialize = deserialize;
var moduleNames = {};
var getElectronBinding$1 = {};
Object.defineProperty(getElectronBinding$1, "__esModule", { value: true });
getElectronBinding$1.getElectronBinding = void 0;
const getElectronBinding = (name) => {
  if (process._linkedBinding) {
    return process._linkedBinding("electron_common_" + name);
  } else if (process.electronBinding) {
    return process.electronBinding(name);
  } else {
    return null;
  }
};
getElectronBinding$1.getElectronBinding = getElectronBinding;
(function(exports) {
  var _a, _b;
  Object.defineProperty(exports, "__esModule", { value: true });
  exports.browserModuleNames = exports.commonModuleNames = void 0;
  const get_electron_binding_1 = getElectronBinding$1;
  exports.commonModuleNames = [
    "clipboard",
    "nativeImage",
    "shell"
  ];
  exports.browserModuleNames = [
    "app",
    "autoUpdater",
    "BaseWindow",
    "BrowserView",
    "BrowserWindow",
    "contentTracing",
    "crashReporter",
    "dialog",
    "globalShortcut",
    "ipcMain",
    "inAppPurchase",
    "Menu",
    "MenuItem",
    "nativeTheme",
    "net",
    "netLog",
    "MessageChannelMain",
    "Notification",
    "powerMonitor",
    "powerSaveBlocker",
    "protocol",
    "pushNotifications",
    "safeStorage",
    "screen",
    "session",
    "ShareMenu",
    "systemPreferences",
    "TopLevelWindow",
    "TouchBar",
    "Tray",
    "utilityProcess",
    "View",
    "webContents",
    "WebContentsView",
    "webFrameMain"
  ].concat(exports.commonModuleNames);
  const features = get_electron_binding_1.getElectronBinding("features");
  if (((_a = features === null || features === void 0 ? void 0 : features.isDesktopCapturerEnabled) === null || _a === void 0 ? void 0 : _a.call(features)) !== false) {
    exports.browserModuleNames.push("desktopCapturer");
  }
  if (((_b = features === null || features === void 0 ? void 0 : features.isViewApiEnabled) === null || _b === void 0 ? void 0 : _b.call(features)) !== false) {
    exports.browserModuleNames.push("ImageView");
  }
})(moduleNames);
(function(exports) {
  Object.defineProperty(exports, "__esModule", { value: true });
  exports.createFunctionWithReturnValue = exports.getGlobal = exports.getCurrentWebContents = exports.getCurrentWindow = exports.getBuiltin = void 0;
  const callbacks_registry_1 = callbacksRegistry;
  const type_utils_1 = typeUtils;
  const electron_12 = require$$0;
  const module_names_1 = moduleNames;
  const get_electron_binding_1 = getElectronBinding$1;
  const { Promise: Promise2 } = commonjsGlobal;
  const callbacksRegistry$1 = new callbacks_registry_1.CallbacksRegistry();
  const remoteObjectCache = /* @__PURE__ */ new Map();
  const finalizationRegistry = new FinalizationRegistry((id) => {
    const ref = remoteObjectCache.get(id);
    if (ref !== void 0 && ref.deref() === void 0) {
      remoteObjectCache.delete(id);
      electron_12.ipcRenderer.send("REMOTE_BROWSER_DEREFERENCE", contextId, id, 0);
    }
  });
  const electronIds = /* @__PURE__ */ new WeakMap();
  const isReturnValue = /* @__PURE__ */ new WeakSet();
  function getCachedRemoteObject(id) {
    const ref = remoteObjectCache.get(id);
    if (ref !== void 0) {
      const deref = ref.deref();
      if (deref !== void 0)
        return deref;
    }
  }
  function setCachedRemoteObject(id, value) {
    const wr = new WeakRef(value);
    remoteObjectCache.set(id, wr);
    finalizationRegistry.register(value, id);
    return value;
  }
  function getContextId() {
    const v8Util = get_electron_binding_1.getElectronBinding("v8_util");
    if (v8Util) {
      return v8Util.getHiddenValue(commonjsGlobal, "contextId");
    } else {
      throw new Error("Electron >=v13.0.0-beta.6 required to support sandboxed renderers");
    }
  }
  const contextId = process.contextId || getContextId();
  process.on("exit", () => {
    const command = "REMOTE_BROWSER_CONTEXT_RELEASE";
    electron_12.ipcRenderer.send(command, contextId);
  });
  const IS_REMOTE_PROXY = Symbol("is-remote-proxy");
  function wrapArgs(args, visited = /* @__PURE__ */ new Set()) {
    const valueToMeta = (value) => {
      if (visited.has(value)) {
        return {
          type: "value",
          value: null
        };
      }
      if (value && value.constructor && value.constructor.name === "NativeImage") {
        return { type: "nativeimage", value: type_utils_1.serialize(value) };
      } else if (Array.isArray(value)) {
        visited.add(value);
        const meta = {
          type: "array",
          value: wrapArgs(value, visited)
        };
        visited.delete(value);
        return meta;
      } else if (value instanceof Buffer) {
        return {
          type: "buffer",
          value
        };
      } else if (type_utils_1.isSerializableObject(value)) {
        return {
          type: "value",
          value
        };
      } else if (typeof value === "object") {
        if (type_utils_1.isPromise(value)) {
          return {
            type: "promise",
            then: valueToMeta(function(onFulfilled, onRejected) {
              value.then(onFulfilled, onRejected);
            })
          };
        } else if (electronIds.has(value)) {
          return {
            type: "remote-object",
            id: electronIds.get(value)
          };
        }
        const meta = {
          type: "object",
          name: value.constructor ? value.constructor.name : "",
          members: []
        };
        visited.add(value);
        for (const prop in value) {
          meta.members.push({
            name: prop,
            value: valueToMeta(value[prop])
          });
        }
        visited.delete(value);
        return meta;
      } else if (typeof value === "function" && isReturnValue.has(value)) {
        return {
          type: "function-with-return-value",
          value: valueToMeta(value())
        };
      } else if (typeof value === "function") {
        return {
          type: "function",
          id: callbacksRegistry$1.add(value),
          location: callbacksRegistry$1.getLocation(value),
          length: value.length
        };
      } else {
        return {
          type: "value",
          value
        };
      }
    };
    return args.map(valueToMeta);
  }
  function setObjectMembers(ref, object, metaId, members) {
    if (!Array.isArray(members))
      return;
    for (const member of members) {
      if (Object.prototype.hasOwnProperty.call(object, member.name))
        continue;
      const descriptor = { enumerable: member.enumerable };
      if (member.type === "method") {
        const remoteMemberFunction = function(...args) {
          let command;
          if (this && this.constructor === remoteMemberFunction) {
            command = "REMOTE_BROWSER_MEMBER_CONSTRUCTOR";
          } else {
            command = "REMOTE_BROWSER_MEMBER_CALL";
          }
          const ret = electron_12.ipcRenderer.sendSync(command, contextId, metaId, member.name, wrapArgs(args));
          return metaToValue(ret);
        };
        let descriptorFunction = proxyFunctionProperties(remoteMemberFunction, metaId, member.name);
        descriptor.get = () => {
          descriptorFunction.ref = ref;
          return descriptorFunction;
        };
        descriptor.set = (value) => {
          descriptorFunction = value;
          return value;
        };
        descriptor.configurable = true;
      } else if (member.type === "get") {
        descriptor.get = () => {
          const command = "REMOTE_BROWSER_MEMBER_GET";
          const meta = electron_12.ipcRenderer.sendSync(command, contextId, metaId, member.name);
          return metaToValue(meta);
        };
        if (member.writable) {
          descriptor.set = (value) => {
            const args = wrapArgs([value]);
            const command = "REMOTE_BROWSER_MEMBER_SET";
            const meta = electron_12.ipcRenderer.sendSync(command, contextId, metaId, member.name, args);
            if (meta != null)
              metaToValue(meta);
            return value;
          };
        }
      }
      Object.defineProperty(object, member.name, descriptor);
    }
  }
  function setObjectPrototype(ref, object, metaId, descriptor) {
    if (descriptor === null)
      return;
    const proto = {};
    setObjectMembers(ref, proto, metaId, descriptor.members);
    setObjectPrototype(ref, proto, metaId, descriptor.proto);
    Object.setPrototypeOf(object, proto);
  }
  function proxyFunctionProperties(remoteMemberFunction, metaId, name) {
    let loaded = false;
    const loadRemoteProperties = () => {
      if (loaded)
        return;
      loaded = true;
      const command = "REMOTE_BROWSER_MEMBER_GET";
      const meta = electron_12.ipcRenderer.sendSync(command, contextId, metaId, name);
      setObjectMembers(remoteMemberFunction, remoteMemberFunction, meta.id, meta.members);
    };
    return new Proxy(remoteMemberFunction, {
      set: (target, property, value) => {
        if (property !== "ref")
          loadRemoteProperties();
        target[property] = value;
        return true;
      },
      get: (target, property) => {
        if (property === IS_REMOTE_PROXY)
          return true;
        if (!Object.prototype.hasOwnProperty.call(target, property))
          loadRemoteProperties();
        const value = target[property];
        if (property === "toString" && typeof value === "function") {
          return value.bind(target);
        }
        return value;
      },
      ownKeys: (target) => {
        loadRemoteProperties();
        return Object.getOwnPropertyNames(target);
      },
      getOwnPropertyDescriptor: (target, property) => {
        const descriptor = Object.getOwnPropertyDescriptor(target, property);
        if (descriptor)
          return descriptor;
        loadRemoteProperties();
        return Object.getOwnPropertyDescriptor(target, property);
      }
    });
  }
  function metaToValue(meta) {
    if (!meta)
      return {};
    if (meta.type === "value") {
      return meta.value;
    } else if (meta.type === "array") {
      return meta.members.map((member) => metaToValue(member));
    } else if (meta.type === "nativeimage") {
      return type_utils_1.deserialize(meta.value);
    } else if (meta.type === "buffer") {
      return Buffer.from(meta.value.buffer, meta.value.byteOffset, meta.value.byteLength);
    } else if (meta.type === "promise") {
      return Promise2.resolve({ then: metaToValue(meta.then) });
    } else if (meta.type === "error") {
      return metaToError(meta);
    } else if (meta.type === "exception") {
      if (meta.value.type === "error") {
        throw metaToError(meta.value);
      } else {
        throw new Error(`Unexpected value type in exception: ${meta.value.type}`);
      }
    } else {
      let ret;
      if ("id" in meta) {
        const cached = getCachedRemoteObject(meta.id);
        if (cached !== void 0) {
          return cached;
        }
      }
      if (meta.type === "function") {
        const remoteFunction = function(...args) {
          let command;
          if (this && this.constructor === remoteFunction) {
            command = "REMOTE_BROWSER_CONSTRUCTOR";
          } else {
            command = "REMOTE_BROWSER_FUNCTION_CALL";
          }
          const obj = electron_12.ipcRenderer.sendSync(command, contextId, meta.id, wrapArgs(args));
          return metaToValue(obj);
        };
        ret = remoteFunction;
      } else {
        ret = {};
      }
      setObjectMembers(ret, ret, meta.id, meta.members);
      setObjectPrototype(ret, ret, meta.id, meta.proto);
      if (ret.constructor && ret.constructor[IS_REMOTE_PROXY]) {
        Object.defineProperty(ret.constructor, "name", { value: meta.name });
      }
      electronIds.set(ret, meta.id);
      setCachedRemoteObject(meta.id, ret);
      return ret;
    }
  }
  function metaToError(meta) {
    const obj = meta.value;
    for (const { name, value } of meta.members) {
      obj[name] = metaToValue(value);
    }
    return obj;
  }
  function hasSenderId(input) {
    return typeof input.senderId === "number";
  }
  function handleMessage(channel, handler) {
    electron_12.ipcRenderer.on(channel, (event, passedContextId, id, ...args) => {
      if (hasSenderId(event)) {
        if (event.senderId !== 0 && event.senderId !== void 0) {
          console.error(`Message ${channel} sent by unexpected WebContents (${event.senderId})`);
          return;
        }
      }
      if (passedContextId === contextId) {
        handler(id, ...args);
      } else {
        electron_12.ipcRenderer.send("REMOTE_BROWSER_WRONG_CONTEXT_ERROR", contextId, passedContextId, id);
      }
    });
  }
  const enableStacks = process.argv.includes("--enable-api-filtering-logging");
  function getCurrentStack() {
    const target = { stack: void 0 };
    if (enableStacks) {
      Error.captureStackTrace(target, getCurrentStack);
    }
    return target.stack;
  }
  handleMessage("REMOTE_RENDERER_CALLBACK", (id, args) => {
    callbacksRegistry$1.apply(id, metaToValue(args));
  });
  handleMessage("REMOTE_RENDERER_RELEASE_CALLBACK", (id) => {
    callbacksRegistry$1.remove(id);
  });
  exports.require = (module) => {
    const command = "REMOTE_BROWSER_REQUIRE";
    const meta = electron_12.ipcRenderer.sendSync(command, contextId, module, getCurrentStack());
    return metaToValue(meta);
  };
  function getBuiltin(module) {
    const command = "REMOTE_BROWSER_GET_BUILTIN";
    const meta = electron_12.ipcRenderer.sendSync(command, contextId, module, getCurrentStack());
    return metaToValue(meta);
  }
  exports.getBuiltin = getBuiltin;
  function getCurrentWindow() {
    const command = "REMOTE_BROWSER_GET_CURRENT_WINDOW";
    const meta = electron_12.ipcRenderer.sendSync(command, contextId, getCurrentStack());
    return metaToValue(meta);
  }
  exports.getCurrentWindow = getCurrentWindow;
  function getCurrentWebContents() {
    const command = "REMOTE_BROWSER_GET_CURRENT_WEB_CONTENTS";
    const meta = electron_12.ipcRenderer.sendSync(command, contextId, getCurrentStack());
    return metaToValue(meta);
  }
  exports.getCurrentWebContents = getCurrentWebContents;
  function getGlobal(name) {
    const command = "REMOTE_BROWSER_GET_GLOBAL";
    const meta = electron_12.ipcRenderer.sendSync(command, contextId, name, getCurrentStack());
    return metaToValue(meta);
  }
  exports.getGlobal = getGlobal;
  Object.defineProperty(exports, "process", {
    enumerable: true,
    get: () => exports.getGlobal("process")
  });
  function createFunctionWithReturnValue(returnValue) {
    const func = () => returnValue;
    isReturnValue.add(func);
    return func;
  }
  exports.createFunctionWithReturnValue = createFunctionWithReturnValue;
  const addBuiltinProperty = (name) => {
    Object.defineProperty(exports, name, {
      enumerable: true,
      get: () => exports.getBuiltin(name)
    });
  };
  module_names_1.browserModuleNames.forEach(addBuiltinProperty);
})(remote);
(function(exports) {
  var __createBinding = commonjsGlobal && commonjsGlobal.__createBinding || (Object.create ? function(o, m, k, k2) {
    if (k2 === void 0) k2 = k;
    Object.defineProperty(o, k2, { enumerable: true, get: function() {
      return m[k];
    } });
  } : function(o, m, k, k2) {
    if (k2 === void 0) k2 = k;
    o[k2] = m[k];
  });
  var __exportStar = commonjsGlobal && commonjsGlobal.__exportStar || function(m, exports2) {
    for (var p in m) if (p !== "default" && !Object.prototype.hasOwnProperty.call(exports2, p)) __createBinding(exports2, m, p);
  };
  Object.defineProperty(exports, "__esModule", { value: true });
  if (process.type === "browser")
    throw new Error(`"@electron/remote" cannot be required in the browser process. Instead require("@electron/remote/main").`);
  __exportStar(remote, exports);
})(renderer$1);
var renderer = renderer$1;
const listeners = /* @__PURE__ */ new Map();
require$$0.contextBridge.exposeInMainWorld("electron", {
  ipcRenderer: {
    invoke: (channel, ...args) => {
      return require$$0.ipcRenderer.invoke(channel, ...args);
    },
    on: (channel, func) => {
      var _a;
      if (!listeners.has(channel)) {
        listeners.set(channel, []);
      }
      const wrappedFunc = (_, ...args) => func(...args);
      (_a = listeners.get(channel)) == null ? void 0 : _a.push(wrappedFunc);
      require$$0.ipcRenderer.on(channel, wrappedFunc);
    },
    removeListener: (channel, func) => {
      const wrappedFuncs = listeners.get(channel) || [];
      const index = wrappedFuncs.indexOf(func);
      if (index > -1) {
        const wrappedFunc = wrappedFuncs[index];
        require$$0.ipcRenderer.removeListener(channel, wrappedFunc);
        wrappedFuncs.splice(index, 1);
      }
    },
    send: (channel, ...args) => {
      require$$0.ipcRenderer.send(channel, ...args);
    },
    "terminal:create": (options) => require$$0.ipcRenderer.invoke("terminal:create", options),
    "terminal:write": (processId, data) => require$$0.ipcRenderer.invoke("terminal:write", processId, data),
    "terminal:resize": (processId, cols, rows) => require$$0.ipcRenderer.invoke("terminal:resize", processId, cols, rows),
    "terminal:dispose": (processId) => require$$0.ipcRenderer.invoke("terminal:dispose", processId)
  }
});
window.addEventListener("unload", () => {
  listeners.forEach((funcs, channel) => {
    funcs.forEach((func) => {
      require$$0.ipcRenderer.removeListener(channel, func);
    });
  });
  listeners.clear();
});
window.addEventListener("DOMContentLoaded", () => {
  const replaceText = (selector, text) => {
    const element = document.getElementById(selector);
    if (element) element.innerText = text;
  };
  for (const type of ["chrome", "node", "electron"]) {
    replaceText(`${type}-version`, process.versions[type]);
  }
});
require$$0.contextBridge.exposeInMainWorld("myAPI", {
  dialog: {
    showOpenDialog: (options) => renderer.dialog.showOpenDialog(options)
  },
  mcp: {
    listServers: () => require$$0.ipcRenderer.invoke("mcp:list-servers"),
    addServer: (server) => require$$0.ipcRenderer.invoke("mcp:add-server", server),
    updateServer: (server) => require$$0.ipcRenderer.invoke("mcp:update-server", server),
    deleteServer: (serverName) => require$$0.ipcRenderer.invoke("mcp:delete-server", serverName),
    setServerActive: (name, isActive) => require$$0.ipcRenderer.invoke("mcp:set-server-active", { name, isActive }),
    listTools: (serverName) => require$$0.ipcRenderer.invoke("mcp:list-tools", serverName),
    callTool: (params) => require$$0.ipcRenderer.invoke("mcp:call-tool", params),
    cleanup: () => require$$0.ipcRenderer.invoke("mcp:cleanup"),
    setServers: (servers) => require$$0.ipcRenderer.send("mcp:servers-from-renderer", servers)
  },
  // Binary related APIs
  isBinaryExist: (name) => require$$0.ipcRenderer.invoke("app:is-binary-exist", name),
  getBinaryPath: (name) => require$$0.ipcRenderer.invoke("app:get-binary-path", name),
  installUVBinary: () => require$$0.ipcRenderer.invoke("app:install-uv-binary"),
  installBunBinary: () => require$$0.ipcRenderer.invoke("app:install-bun-binary")
});
