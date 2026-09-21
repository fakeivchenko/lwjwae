// The page-side bridge runtime. BridgeProtocol loads this file and fills the ${...} placeholders.
(function () {
    if (window.${channel}) return;
    const pending = new Map();
    const listeners = new Map();
    let counter = 0;
    const post = ${post};
    const codec = ${codec};
    const on = (name, listener) => {
        if (!listeners.has(name)) listeners.set(name, new Set());
        listeners.get(name).add(listener);
        return () => off(name, listener);
    };
    const off = (name, listener) => { listeners.get(name)?.delete(listener); };
    window.${channel} = {
        call(name, payload, typed) {
            const id = ++counter;
            return new Promise((resolve, reject) => {
                pending.set(id, { resolve, reject, typed });
                const text = typed ? codec.encode(payload === undefined ? null : payload)
                    : payload === undefined ? ""
                    : typeof payload === "string" ? payload
                    : codec ? codec.encode(payload) : String(payload);
                post(id + "${separator}" + name + "${separator}" + text);
            });
        },
        settle(id, value, error) {
            const entry = pending.get(id);
            if (!entry) return;
            pending.delete(id);
            if (error !== null) return entry.reject(new Error(error));
            entry.resolve(entry.typed ? codec.decode(value) : value);
        },
        emit(name, payload, typed) {
            const detail = typed ? codec.decode(payload) : payload;
            listeners.get(name)?.forEach((listener) => {
                try { listener(detail); } catch (error) { console.error(error); }
            });
            window.dispatchEvent(new CustomEvent("${eventPrefix}" + name, { detail }));
        },
        on,
        off
    };
    window.${pageApi} = { on, off };
})();
