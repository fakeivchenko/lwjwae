// The page-side bridge runtime. BridgeProtocol loads this file and fills the ${...} placeholders.
(function () {
    if (window.${channel}) return;
    const pending = new Map();
    const listeners = new Map();
    let counter = 0;
    let listenerIds = 0;
    let eventIds = 0;
    const post = ${post};
    const codec = ${codec};
    const encode = (payload) =>
        payload === undefined ? ""
        : typeof payload === "string" ? payload
        : codec ? codec.encode(payload) : String(payload);
    const call = (name, payload, typed) => {
        const id = ++counter;
        return new Promise((resolve, reject) => {
            pending.set(id, { resolve, reject, typed });
            const text = typed ? codec.encode(payload === undefined ? null : payload) : encode(payload);
            post(id + "${separator}" + name + "${separator}" + text);
        });
    };
    // Events. A listener gets { event, id, payload }; the id counts deliveries in this document.
    const deliver = (name, payload) => {
        const event = { event: name, id: ++eventIds, payload };
        listeners.get(name)?.forEach((handler) => {
            try { handler(event); } catch (error) { console.error(error); }
        });
    };
    const unlisten = (name, id) => { listeners.get(name)?.delete(id); };
    const listen = (name, handler) => {
        if (!listeners.has(name)) listeners.set(name, new Map());
        const id = ++listenerIds;
        listeners.get(name).set(id, handler);
        return Promise.resolve(() => unlisten(name, id));
    };
    const once = (name, handler) => {
        if (!listeners.has(name)) listeners.set(name, new Map());
        const id = ++listenerIds;
        listeners.get(name).set(id, (event) => { unlisten(name, id); handler(event); });
        return Promise.resolve(() => unlisten(name, id));
    };
    // An event from the page goes to the listeners here and to the Java ones, in that order.
    const emit = (name, payload) => {
        deliver(name, payload);
        const typed = payload !== undefined && typeof payload !== "string" && codec !== null;
        return call("${eventCall}", (typed ? "1" : "0") + "${separator}" + name + "${separator}" + encode(payload), false)
            .then(() => undefined);
    };
    window.${channel} = {
        call,
        settle(id, value, error) {
            const entry = pending.get(id);
            if (!entry) return;
            pending.delete(id);
            if (error !== null) return entry.reject(new Error(error));
            entry.resolve(entry.typed ? codec.decode(value) : value);
        },
        deliver(name, payload, typed) {
            deliver(name, typed ? codec.decode(payload) : payload);
        }
    };
    // Windows. open resolves to the id of the new window; close ends this document, so it never
    // resolves. Options mirror WindowParameters: title, width, height, x, y, centered, url, resource.
    const field = (value) => value === undefined || value === null ? "" : String(value);
    // Java reads whole numbers; a size such as innerWidth / 2 is rounded rather than rejected.
    const number = (value) => value === undefined || value === null ? "" : String(Math.round(value));
    const open = (options = {}) =>
        call("${openCall}", [
            field(options.title), number(options.width), number(options.height),
            number(options.x), number(options.y), options.centered ? "1" : "",
            field(options.url), field(options.resource)
        ].join("${separator}"), false).then(Number);
    const close = () => call("${closeCall}", "", false).then(() => undefined);
    window.${pageApi} = { listen, once, emit, open, close };
})();
