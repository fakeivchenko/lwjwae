package dev.ivchenko.lwjwae.instance;

import dev.ivchenko.lwjwae.Application;
import dev.ivchenko.lwjwae.ApplicationParameters;
import dev.ivchenko.lwjwae.BackendProvider;
import dev.ivchenko.lwjwae.testing.FakeApplication;

/** The backend of the tests, so that {@link SingleInstance#create} makes a fake application. */
public class FakeInstanceProvider implements BackendProvider {
  @Override
  public String name() {
    return "fake-instance";
  }

  @Override
  public boolean isSupported() {
    return true;
  }

  @Override
  public int priority() {
    return 0;
  }

  @Override
  public Application create(ApplicationParameters parameters) {
    return new FakeApplication(parameters);
  }
}
