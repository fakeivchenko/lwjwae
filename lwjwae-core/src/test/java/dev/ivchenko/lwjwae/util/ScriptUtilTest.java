package dev.ivchenko.lwjwae.util;

import dev.ivchenko.lwjwae.exception.ScriptEvaluationFailedException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Tests {@link ScriptUtil#quote}, the injection boundary of the bridge. Every escape must hold. */
class ScriptUtilTest {
  @Test
  void wrapsPlainTextInDoubleQuotes() {
    Assertions.assertEquals("\"hello\"", ScriptUtil.quote("hello"));
  }

  @Test
  void nullBecomesTheNullLiteral() {
    Assertions.assertEquals("null", ScriptUtil.quote(null));
  }

  @Test
  void escapesQuotesAndBackslashes() {
    Assertions.assertEquals("\"say \\\"hi\\\" \\\\ bye\"", ScriptUtil.quote("say \"hi\" \\ bye"));
  }

  @Test
  void escapesControlCharacters() {
    String input = "a\nb\rc\td" + (char) 0 + "e" + (char) 0x1f + "z";
    Assertions.assertEquals("\"a\\nb\\rc\\td\\u0000e\\u001fz\"", ScriptUtil.quote(input));
  }

  @Test
  void escapesLineAndParagraphSeparators() {
    // Legal in a Java string, a syntax error inside a JavaScript string literal before ES2019.
    String input = "x\u2028y\u2029z";
    Assertions.assertEquals("\"x\\u2028y\\u2029z\"", ScriptUtil.quote(input));
  }

  @Test
  void cannotBreakOutOfTheLiteral() {
    String quoted = ScriptUtil.quote("\"; alert(1); //");
    // Only the two delimiters are unescaped quotes.
    Assertions.assertEquals("\"\\\"; alert(1); //\"", quoted);
  }

  @Test
  void leavesUnicodeTextAlone() {
    Assertions.assertEquals("\"привет, 世界\"", ScriptUtil.quote("привет, 世界"));
  }

  @Test
  void taggedEvaluationQuotesTheScriptIntoTheWrapper() {
    String wrapped = ScriptUtil.taggedEvaluation("1 + \"1\"");
    Assertions.assertTrue(wrapped.contains("(0, eval)(\"1 + \\\"1\\\"\")"), wrapped);
    Assertions.assertTrue(wrapped.startsWith("(function () {"));
  }

  @Test
  void completeTaggedDecodesValuesErrorsAndNothing() throws Exception {
    CompletableFuture<String> value = new CompletableFuture<>();
    ScriptUtil.completeTagged(value, "S42");
    Assertions.assertEquals("42", value.get());

    CompletableFuture<String> nothing = new CompletableFuture<>();
    ScriptUtil.completeTagged(nothing, null);
    Assertions.assertEquals("undefined", nothing.get());

    CompletableFuture<String> failure = new CompletableFuture<>();
    ScriptUtil.completeTagged(failure, "Ekaboom");
    ExecutionException thrown = Assertions.assertThrows(ExecutionException.class, failure::get);
    Assertions.assertInstanceOf(ScriptEvaluationFailedException.class, thrown.getCause());
    Assertions.assertEquals("kaboom", thrown.getCause().getMessage());
  }
}
