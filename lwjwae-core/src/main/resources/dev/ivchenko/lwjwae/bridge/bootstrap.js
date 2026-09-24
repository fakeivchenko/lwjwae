// The page-side bridge runtime. BridgeProtocol loads this file and fills the ${...} placeholders.
// Everything goes through RPC: bindings, window.lwjwae.emit, open and close are calls, and the events
// of Java arrive in the answer of one call that the document keeps open.
(function () {
    if (window.${channel}) return;
    const separator = "${separator}";
    const post = ${post};
    const codec = ${codec};
    const rpc = ${rpc};
    // Only a document of an origin that the window trusts learns the token, which is what lets a
    // message through on the Java side. The check runs before any script of the document does.
    const trusted = ${trusted}.includes(location.origin)
        || (window.top === window && location.href === "about:blank");
    const token = trusted ? ${token} : null;
    // Answers name the document they're meant for, so the answers to a document that the window
    // left can't land in this one.
    const doc = crypto.randomUUID ? crypto.randomUUID() : String(Math.random()).slice(2);
    const tag = "\u0001rpc", cancelTag = "\u0001rpc-cancel";
    const valueType = "application/x-lwjwae-value;charset=utf-8";
    const textType = "text/plain;charset=utf-8";

    // Values go through the codec of the window, or JSON without one; text that isn't a value goes
    // as it is, and without a codec anything else goes as String(payload).
    const encodeValue = (value) => codec ? codec.encode(value === undefined ? null : value) : JSON.stringify(value === undefined ? null : value);
    const decodeValue = (text) => text === "" ? undefined : codec ? codec.decode(text) : JSON.parse(text);
    const textOf = (payload) =>
        payload === undefined ? ""
        : typeof payload === "string" ? payload
        : codec ? codec.encode(payload) : String(payload);
    class RpcError extends Error {
        constructor(status, code, message) {
            super(message);
            this.name = "RpcError";
            this.status = status;
            this.code = code;
        }
    }
    const errorOf = (status, text) => {
        let code = null, message = "HTTP " + status;
        try { const error = JSON.parse(text); code = error.code; message = error.error || message; } catch (_) {}
        return new RpcError(status, code, message);
    };
    const rpcBody = (body) =>
        body === undefined || body === null ? { data: undefined, type: undefined }
        : typeof body === "object" && "__lwjwaeValue" in body ? { data: body.__lwjwaeValue, type: valueType }
        : typeof body === "string" ? { data: body, type: textType }
        : body instanceof ArrayBuffer || ArrayBuffer.isView(body) || body instanceof Blob
            ? { data: body, type: "application/octet-stream" }
        : { data: encodeValue(body), type: valueType };
    const rpcCheck = async (response) => {
        if (response.ok) return response;
        throw errorOf(response.status, await response.text());
    };
    const forbidden = () => Promise.reject(new RpcError(403, "forbidden", "This document may not call Java"));
    const decode64 = Uint8Array.fromBase64 ? (text) => Uint8Array.fromBase64(text) : (text) => {
        const raw = atob(text); const bytes = new Uint8Array(raw.length);
        for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
        return bytes;
    };
    const encode64 = (bytes) => {
        if (bytes.toBase64) return bytes.toBase64();
        let raw = ""; for (let i = 0; i < bytes.length; i += 32768) raw += String.fromCharCode.apply(null, bytes.subarray(i, i + 32768));
        return btoa(raw);
    };
    // The first count - 1 fields of text, and the rest of it as the last one: a body may contain
    // the separator.
    const fields = (text, count) => {
        const result = [];
        let start = 0;
        for (let i = 1; i < count; i++) {
            const end = text.indexOf(separator, start);
            if (end < 0) break;
            result.push(text.slice(start, end));
            start = end + 1;
        }
        result.push(text.slice(start));
        return result;
    };

    // Calls over the message channel. A whole answer is one message, r; a streamed one is h, the
    // parts, and e. A part carries its number: text messages and WebView2 shared buffers are two
    // kinds of events, and nothing promises that they arrive in the order they were sent. A raw
    // call resolves to { status, text } instead of a Response, which saves a small call the work.
    const calls = new Map();
    let nextCall = 0;
    const settle = (call, status, text) => {
        calls.delete(call.id);
        if (status >= 400) call.reject(errorOf(status, text));
        else call.resolve({ status, text });
    };
    const part = (call, seq, bytes) => {
        call.waiting.set(seq, bytes);
        while (call.waiting.has(call.next)) {
            call.controller.enqueue(call.waiting.get(call.next));
            call.waiting.delete(call.next);
            call.next++;
        }
        finishIfDone(call);
    };
    const finishIfDone = (call) => {
        if (call.expected !== null && call.next >= call.expected) { calls.delete(call.id); call.controller.close(); }
    };
    const receive = (message) => {
        const [, callDoc, id, kind, rest] = fields(message, 5);
        const call = callDoc === doc ? calls.get(id) : undefined;
        if (!call) return;
        if (kind === "r") {
            const [status, type, encoding, body] = fields(rest, 4);
            const code = Number(status);
            if (call.raw) {
                settle(call, code, encoding === "s" ? body : new TextDecoder().decode(decode64(body)));
                return;
            }
            calls.delete(id);
            const nullBody = code === 204 || code === 205 || code === 304;
            const data = nullBody ? null : encoding === "s" ? body : decode64(body);
            call.resolve(rpcCheck(new Response(data, { status: code, headers: type ? { "Content-Type": type } : {} })));
        } else if (kind === "h") {
            const [status, type] = fields(rest, 2);
            const code = Number(status);
            const nullBody = code === 204 || code === 205 || code === 304;
            const response = new Response(nullBody ? null : call.stream, { status: code, headers: type ? { "Content-Type": type } : {} });
            if (nullBody) calls.delete(id);
            if (call.raw) response.text().then((text) => settle(call, code, text), call.reject);
            else call.resolve(rpcCheck(response));
        } else if (kind === "d") {
            const [seq, data] = fields(rest, 2);
            part(call, Number(seq), decode64(data));
        } else if (kind === "e") {
            call.expected = Number(rest);
            finishIfDone(call);
        }
    };
    if (rpc.webview2) {
        window.chrome.webview.addEventListener("message", (event) => {
            if (typeof event.data === "string" && event.data.startsWith(tag + separator)) receive(event.data);
        });
        window.chrome.webview.addEventListener("sharedbufferreceived", (event) => {
            const data = event.additionalData || {};
            const call = data.doc === doc ? calls.get(data.id) : undefined;
            const buffer = event.getBuffer();
            // The buffer is gone once released, so the page keeps a copy.
            if (call) part(call, data.seq, new Uint8Array(buffer).slice());
            window.chrome.webview.releaseBuffer(buffer);
        });
    }
    const callMessage = async (name, body, options = {}, raw = false) => {
        if (token === null) return forbidden();
        const { data, type } = rpcBody(body);
        let encoding = "s", payload = "";
        if (data instanceof Blob) { encoding = "b"; payload = encode64(new Uint8Array(await data.arrayBuffer())); }
        else if (data instanceof ArrayBuffer) { encoding = "b"; payload = encode64(new Uint8Array(data)); }
        else if (ArrayBuffer.isView(data)) { encoding = "b"; payload = encode64(new Uint8Array(data.buffer, data.byteOffset, data.byteLength)); }
        else if (data !== undefined) { payload = data; }
        const id = String(++nextCall);
        return new Promise((resolve, reject) => {
            const call = { id, raw, resolve, reject, next: 0, waiting: new Map(), expected: null, controller: null, stream: null };
            const cancel = () => post(cancelTag + separator + token + separator + doc + separator + id);
            call.stream = new ReadableStream({
                start(controller) { call.controller = controller; },
                cancel() { if (calls.delete(id)) cancel(); }
            });
            calls.set(id, call);
            const signal = options.signal;
            if (signal) {
                const abort = () => {
                    if (!calls.has(id)) return;
                    calls.delete(id);
                    cancel();
                    const error = new DOMException("The call was aborted", "AbortError");
                    try { call.controller.error(error); } catch (_) {}
                    reject(error);
                };
                if (signal.aborted) return abort();
                signal.addEventListener("abort", abort, { once: true });
            }
            post([tag, token, doc, id, name, type || "", encoding, payload].join(separator));
        });
    };
    const callFetch = (name, body, options = {}) => {
        if (token === null) return forbidden();
        const { data, type } = rpcBody(body);
        return fetch(rpc.base + encodeURIComponent(name), {
            method: "POST", body: data, headers: type ? { "Content-Type": type } : {}, signal: options.signal
        }).then(rpcCheck);
    };
    // lwjwae.call streams where the engine does: fetch on the scheme of the application. A small
    // call, whose answer is text, takes the message channel, which costs less per call.
    const callRpc = rpc.base ? callFetch : callMessage;
    const callText = (name, body) => callMessage(name, body, {}, true);
    const rpcInvoke = async (name, value, options) => {
        const { text } = await callMessage(name, { __lwjwaeValue: encodeValue(value) }, options, true);
        return decodeValue(text);
    };
    // window.NAME(payload) of a binding: text in and out, or a value of the codec when it's typed.
    const bound = async (name, payload, typed) => {
        if (typed) {
            const { text } = await callText(name, { __lwjwaeValue: codec.encode(payload === undefined ? null : payload) });
            return codec.decode(text);
        }
        const { status, text } = await callText(name, textOf(payload));
        return status === 204 ? null : text;
    };

    // Events. A listener gets { event, id, payload }; the id counts deliveries in this document.
    const listeners = new Map();
    let listenerIds = 0;
    let eventIds = 0;
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
        return callText("${eventCall}", (typed ? "1" : "0") + separator + name + separator + textOf(payload))
            .then(() => undefined);
    };
    // The events of Java: frames of four bytes of length and typed␟name␟payload in UTF-8, in the
    // answer of one call that the top-level document opens now and reads until it goes away. The
    // call takes the message channel on every engine: WebKitGTK holds back the tail of a fetch
    // stream that the page fell behind on until more data arrives, which would hold an event back
    // until the next one.
    const readEvents = async (signal) => {
        const reader = (await callMessage("${eventsCall}", undefined, { signal })).body.getReader();
        const decoder = new TextDecoder();
        let buffer = new Uint8Array(0);
        for (;;) {
            const { done, value } = await reader.read();
            if (done) return;
            if (buffer.length === 0) buffer = value;
            else { const joined = new Uint8Array(buffer.length + value.length); joined.set(buffer); joined.set(value, buffer.length); buffer = joined; }
            let offset = 0;
            while (buffer.length - offset >= 4) {
                const length = ((buffer[offset] << 24) | (buffer[offset + 1] << 16) | (buffer[offset + 2] << 8) | buffer[offset + 3]) >>> 0;
                if (buffer.length - offset - 4 < length) break;
                const [typed, name, payload] = fields(decoder.decode(buffer.subarray(offset + 4, offset + 4 + length)), 3);
                offset += 4 + length;
                deliver(name, typed === "1" && codec ? codec.decode(payload) : payload);
            }
            buffer = buffer.subarray(offset);
        }
    };
    // A document that goes away gives the stream up before the next one asks for it, through the
    // same channel, so the events in between wait for the next document rather than go to this one.
    // One that comes back from the back-forward cache asks again.
    if (token !== null && window.top === window) {
        let events = null;
        const subscribe = () => {
            events = new AbortController();
            readEvents(events.signal).catch((error) => { if (error?.name !== "AbortError") console.error("lwjwae events:", error); });
        };
        subscribe();
        window.addEventListener("pagehide", () => events.abort());
        window.addEventListener("pageshow", (event) => { if (event.persisted) subscribe(); });
    }

    // Windows. open resolves to the id of the new window; close ends this document, so it never
    // resolves. Options mirror WindowParameters: title, width, height, x, y, centered, url, resource.
    const field = (value) => value === undefined || value === null ? "" : String(value);
    // Java reads whole numbers; a size such as innerWidth / 2 is rounded rather than rejected.
    const number = (value) => value === undefined || value === null ? "" : String(Math.round(value));
    const open = (options = {}) =>
        callText("${openCall}", [
            field(options.title), number(options.width), number(options.height),
            number(options.x), number(options.y), options.centered ? "1" : "",
            field(options.url), field(options.resource)
        ].join(separator)).then(({ text }) => Number(text));
    const close = () => callText("${closeCall}", "").then(() => undefined);

    window.${channel} = { receive, bound };
    window.${pageApi} = { listen, once, emit, open, close, call: callRpc, invoke: rpcInvoke, RpcError };
})();
