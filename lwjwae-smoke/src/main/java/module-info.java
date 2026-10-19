/**
 * The smoke test of lwjwae as an application on the module path: it reads only the exported API,
 * finds its backend as a service, and opens the package of its page.
 *
 * <p>Suppressed warnings: {@code opens}: the package holds the files of the page and no class,
 * which the compiler takes for an empty package; the module system finds it all the same.
 */
@SuppressWarnings("opens")
module dev.ivchenko.lwjwae.smoke {
  requires dev.ivchenko.lwjwae;

  opens smoke;
}
