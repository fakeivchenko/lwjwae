package dev.ivchenko.lwjwae.store;

import dev.ivchenko.lwjwae.util.JsonUtil;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * The commands that a page sends to the store through {@code lwjwae.store}, as JSON.
 *
 * <p>A command is {@code {"op": "query", "sql": ..., "params": [...] or {...}}}, the same with
 * {@code "execute"}, {@code {"op": "script", "sql": ...}}, or {@code {"op": "transaction",
 * "statements": [{"sql": ..., "params": ...}, ...]}}. The answer is the rows of a query, {@code
 * {"changes": n, "lastInsertRowId": id}} for an execute, nothing for a script, and the rows of
 * every statement of a transaction, which {@code RETURNING} fills for a write. JSON has no bytes,
 * so a {@code BLOB} goes to the page as Base64.
 */
@UtilityClass
public class StorePageCommands {
  /**
   * Runs the command in {@code json} on {@code store}, and returns its answer as JSON, or an empty
   * string for none.
   *
   * @throws IllegalArgumentException If the command isn't one, or its statement has the wrong
   *     number of statements or parameters.
   */
  public String run(Store store, String json) {
    if (!(JsonUtil.parse(json) instanceof Map<?, ?> command)) {
      throw new IllegalArgumentException("A command is a JSON object: " + json);
    }
    String op = command.get("op") instanceof String name ? name : "";
    return switch (op) {
      case "query" ->
          JsonUtil.write(
              StorePageCommands.forPage(
                  StorePageCommands.query(store, command.get("sql"), command.get("params"))));
      case "execute" -> {
        int changes = StorePageCommands.execute(store, command.get("sql"), command.get("params"));
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("changes", changes);
        answer.put("lastInsertRowId", store.lastInsertRowId());
        yield JsonUtil.write(answer);
      }
      case "script" -> {
        store.executeScript(StorePageCommands.sql(command.get("sql")));
        yield "";
      }
      case "transaction" -> {
        if (!(command.get("statements") instanceof List<?> statements)) {
          throw new IllegalArgumentException("A transaction has a list of statements");
        }
        List<Object> answers =
            store.inTransaction(
                _ -> {
                  List<Object> rows = new ArrayList<>();
                  for (Object statement : statements) {
                    if (!(statement instanceof Map<?, ?> fields)) {
                      throw new IllegalArgumentException("A statement is {sql, params}");
                    }
                    rows.add(
                        StorePageCommands.forPage(
                            StorePageCommands.query(
                                store, fields.get("sql"), fields.get("params"))));
                  }
                  return rows;
                });
        yield JsonUtil.write(answers);
      }
      default -> throw new IllegalArgumentException("Not a command of the store: " + op);
    };
  }

  private List<Map<String, Object>> query(Store store, Object sql, Object parameters) {
    return switch (parameters) {
      case null -> store.query(StorePageCommands.sql(sql));
      case List<?> list -> store.query(StorePageCommands.sql(sql), list.toArray());
      case Map<?, ?> map -> store.query(StorePageCommands.sql(sql), StorePageCommands.named(map));
      default -> throw new IllegalArgumentException("Parameters are an array or an object");
    };
  }

  private int execute(Store store, Object sql, Object parameters) {
    return switch (parameters) {
      case null -> store.execute(StorePageCommands.sql(sql));
      case List<?> list -> store.execute(StorePageCommands.sql(sql), list.toArray());
      case Map<?, ?> map -> store.execute(StorePageCommands.sql(sql), StorePageCommands.named(map));
      default -> throw new IllegalArgumentException("Parameters are an array or an object");
    };
  }

  private String sql(Object sql) {
    if (!(sql instanceof String text)) {
      throw new IllegalArgumentException("The SQL of a statement is a string");
    }
    return text;
  }

  private Map<String, Object> named(Map<?, ?> map) {
    Map<String, Object> named = new LinkedHashMap<>();
    map.forEach((key, value) -> named.put(String.valueOf(key), value));
    return named;
  }

  /** The rows with every {@code BLOB} as Base64, which JSON can carry. */
  private List<Map<String, Object>> forPage(List<Map<String, Object>> rows) {
    for (Map<String, Object> row : rows) {
      row.replaceAll(
          (_, value) ->
              value instanceof byte[] bytes ? Base64.getEncoder().encodeToString(bytes) : value);
    }
    return rows;
  }
}
