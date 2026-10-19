package dev.ivchenko.lwjwae;

import dev.ivchenko.lwjwae.rpc.RpcHandler;

/**
 * A page call that a module answers.
 *
 * @param capability What the application must grant its pages for the call, or {@code null}.
 * @param handler What answers the call.
 */
record ModuleCall(PageCapability capability, RpcHandler handler) {}
