// window.lwjwae.updates: check resolves to { version, notes, mandatory, installable, size } or null,
// and install downloads the update, reporting to onProgress from 0 to 1, and installs it, which ends
// the application. Both reject with no-update, not-installable, update-rejected, or download-failed.
(bridge) => ({
    check: () => bridge.call("lwjwae:update", "check")
        .then((text) => text === "" ? null : JSON.parse(text)),
    install: async ({ onProgress } = {}) => {
        const stop = typeof onProgress === "function"
            ? await bridge.listen("lwjwae:update-progress", (event) => onProgress(Number(event.payload)))
            : () => {};
        try {
            await bridge.call("lwjwae:update", "install");
        } finally {
            stop();
        }
    }
})
