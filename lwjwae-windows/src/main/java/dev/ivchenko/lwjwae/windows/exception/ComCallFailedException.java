package dev.ivchenko.lwjwae.windows.exception;

import java.io.Serial;
import lombok.Getter;

/** A Win32 or COM call returned a failure {@code HRESULT}. */
@Getter
public class ComCallFailedException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  private final int hresult;

  public ComCallFailedException(String call, int hresult) {
    super("%s failed with HRESULT 0x%08X".formatted(call, hresult));
    this.hresult = hresult;
  }
}
