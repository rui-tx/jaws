package org.ruitx.jaws.components.mimir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.ruitx.jaws.types.Row;

/**
 * RowBinder maps a {@link Row} to lightweight projection types (Java records or simple POJOs).
 *
 * <p>Best Practices
 * - Prefer Java records for small projection shapes
 * - Alias SQL columns to match record component or POJO property/field names (case-insensitive)
 * - Use Optional<T> for nullable DB columns to avoid nulls (Optional-first style)
 * - Keep projections flat; do joins/aggregation in SQL or services
 *
 * <p>Usage
 * <pre>{@code
 * // Projection record
 * public record UserSummary(Optional<Long> id, Optional<String> user) {}
 *
 * // SQL must alias to component names
 * String sql = """
 *   SELECT id AS id, user AS user FROM USER ORDER BY created_at DESC
 * """;
 * List<UserSummary> out = repo.getAll(sql, RowBinder.mapper(UserSummary.class));
 * }
 * </pre>
 *
 * <p>Optional Handling
 * - Optional<T> properties are populated with Optional.of(convertedValue) or Optional.empty()
 * - Inner T is resolved via reflection when available, enabling conversions like Integer->Long
 * - Raw Optional (no generic) wraps raw value without conversion
 *
 * <p>Conversions
 * - Integer <-> Long, Float <-> Double, numeric <-> boolean(0/1), String parsers, byte[] passthrough
 * - Primitive targets default to zero/false when missing
 * - Missing required non-Optional reference fields throws IllegalArgumentException
 *
 * <p>Scope
 * - Intended for flat projections and small DTOs, not full entities
 * - For complex cases or computed fields, use custom mappers
 */
public final class RowBinder {

  private RowBinder() {}

  private static final class RecordMeta {
    final Constructor<?> ctor;
    final String[] componentNames; // in declaration order
    final Class<?>[] componentTypes; // in declaration order
    final Class<?>[] optionalInnerTypes; // nullable entries when not Optional

    RecordMeta(Constructor<?> ctor, String[] names, Class<?>[] types, Class<?>[] optionalInnerTypes) {
      this.ctor = ctor;
      this.componentNames = names;
      this.componentTypes = types;
      this.optionalInnerTypes = optionalInnerTypes;
    }
  }

  private static final class PojoMeta {
    final Constructor<?> noArgCtor;
    final Map<String, Method> setters; // lowercased name -> setter
    final Map<String, Field> fields;   // lowercased name -> field
    final Map<String, Class<?>> optionalInnerForSetter; // property -> inner type or null
    final Map<String, Class<?>> optionalInnerForField;  // property -> inner type or null

    PojoMeta(Constructor<?> ctor,
             Map<String, Method> setters,
             Map<String, Field> fields,
             Map<String, Class<?>> optionalInnerForSetter,
             Map<String, Class<?>> optionalInnerForField) {
      this.noArgCtor = ctor;
      this.setters = setters;
      this.fields = fields;
      this.optionalInnerForSetter = optionalInnerForSetter;
      this.optionalInnerForField = optionalInnerForField;
    }
  }

  private static final ConcurrentHashMap<Class<?>, Object> metaCache = new ConcurrentHashMap<>();

  /** Convenience factory for Repository methods: getAll(sql, RowBinder.mapper(MyProj.class)). */
  public static <T> RowMapper<T> mapper(Class<T> type) {
    return row -> map(row, type);
  }

  /** Map a Row into the given projection type (record or simple POJO). */
  @SuppressWarnings("unchecked")
  public static <T> T map(Row row, Class<T> type) {
    if (row == null) throw new IllegalArgumentException("row must not be null");
    if (type == null) throw new IllegalArgumentException("type must not be null");

    // Build a case-insensitive view of the row
    Map<String, Object> lowerCols = new HashMap<>();
    for (Map.Entry<String, Object> e : row.data().entrySet()) {
      if (e.getKey() != null) {
        lowerCols.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
      }
    }

    Object meta = metaCache.computeIfAbsent(type, RowBinder::introspect);

    try {
      if (type.isRecord()) {
        RecordMeta rm = (RecordMeta) meta;
        Object[] args = new Object[rm.componentTypes.length];
        for (int i = 0; i < rm.componentTypes.length; i++) {
          String name = rm.componentNames[i];
          Class<?> targetType = rm.componentTypes[i];
          Class<?> inner = (rm.optionalInnerTypes != null && i < rm.optionalInnerTypes.length)
              ? rm.optionalInnerTypes[i]
              : null;
          Object bound = bindValue(lowerCols, name, targetType, inner);
          args[i] = bound;
        }
        return (T) rm.ctor.newInstance(args);
      } else {
        PojoMeta pm = (PojoMeta) meta;
        Object instance = pm.noArgCtor.newInstance();
        // For each known property/field, attempt to bind from columns
        // We don't fail for unknown columns; we fail if a required non-Optional ref is missing when a matching property exists.
        for (Map.Entry<String, Method> e : pm.setters.entrySet()) {
          String prop = e.getKey();
          Method setter = e.getValue();
          Class<?> paramType = setter.getParameterTypes()[0];
          Class<?> inner = pm.optionalInnerForSetter.get(prop);
          Object bound = bindValue(lowerCols, prop, paramType, inner);
          if (bound != null || !isOptional(paramType)) {
            setter.invoke(instance, bound);
          }
        }
        for (Map.Entry<String, Field> e : pm.fields.entrySet()) {
          String prop = e.getKey();
          // Skip if setter exists for same property (setter already handled it)
          if (pm.setters.containsKey(prop)) continue;
          Field f = e.getValue();
          Class<?> inner = pm.optionalInnerForField.get(prop);
          Object bound = bindValue(lowerCols, prop, f.getType(), inner);
          if (bound != null || !isOptional(f.getType())) {
            boolean acc = f.canAccess(instance);
            if (!acc) f.setAccessible(true);
            try {
              f.set(instance, bound);
            } finally {
              if (!acc) f.setAccessible(false);
            }
          }
        }
        return (T) instance;
      }
    } catch (IllegalArgumentException iae) {
      throw iae;
    } catch (Exception re) {
      throw new IllegalArgumentException("Failed to bind Row to type " + type.getName() + ": " + re.getMessage(), re);
    }
  }

  private static boolean isOptional(Class<?> cls) {
    return Optional.class.isAssignableFrom(cls);
  }

  private static Object defaultPrimitive(Class<?> primitiveType) {
    if (primitiveType == boolean.class) return false;
    if (primitiveType == byte.class) return (byte) 0;
    if (primitiveType == short.class) return (short) 0;
    if (primitiveType == int.class) return 0;
    if (primitiveType == long.class) return 0L;
    if (primitiveType == float.class) return 0f;
    if (primitiveType == double.class) return 0d;
    if (primitiveType == char.class) return '\0';
    throw new IllegalArgumentException("Unsupported primitive type: " + primitiveType.getName());
  }

  private static Object bindValue(Map<String, Object> lowerCols, String name, Class<?> targetType, Class<?> optionalInnerTarget) {
    String key = name.toLowerCase(Locale.ROOT);
    boolean present = lowerCols.containsKey(key);
    Object raw = present ? lowerCols.get(key) : null;

    if (isOptional(targetType)) {
      // Convert inner to Optional<T> if we know T, otherwise wrap raw
      if (!present || raw == null) return Optional.empty();
      if (optionalInnerTarget != null) {
        Object converted = convert(raw, optionalInnerTarget, name);
        return Optional.of(converted);
      }
      return Optional.of(raw);
    }

    if (!present || raw == null) {
      // For primitives, return default zero/false; for reference, throw to avoid silent nulls
      if (targetType.isPrimitive()) {
        return defaultPrimitive(targetType);
      } else {
        throw new IllegalArgumentException("Missing required column '" + name + "' for type " + targetType.getSimpleName());
      }
    }

    return convert(raw, targetType, name);
  }

  private static Object convert(Object raw, Class<?> targetType, String name) {
    if (raw == null) return null;

    // Direct assignment
    if (targetType.isInstance(raw)) return raw;

    // Handle primitives via wrappers
    if (targetType.isPrimitive()) {
      if (targetType == int.class) return toInteger(raw, name);
      if (targetType == long.class) return toLong(raw, name);
      if (targetType == double.class) return toDouble(raw, name);
      if (targetType == float.class) return toFloat(raw, name);
      if (targetType == boolean.class) return toBoolean(raw, name);
      if (targetType == byte.class) return toByte(raw, name);
      if (targetType == short.class) return toShort(raw, name);
      if (targetType == char.class) return toChar(raw, name);
    }

    // Boxed types and common conversions
    if (targetType == Integer.class) return toInteger(raw, name);
    if (targetType == Long.class) return toLong(raw, name);
    if (targetType == Double.class) return toDouble(raw, name);
    if (targetType == Float.class) return toFloat(raw, name);
    if (targetType == Boolean.class) return toBoolean(raw, name);
    if (targetType == String.class) return String.valueOf(raw);
    if (targetType == byte[].class && raw instanceof byte[] b) return b;

    throw new IllegalArgumentException("Unsupported conversion for column '" + name + "' from "
        + raw.getClass().getSimpleName() + " to " + targetType.getSimpleName());
  }

  private static Integer toInteger(Object raw, String name) {
    if (raw instanceof Integer i) return i;
    if (raw instanceof Long l) return l.intValue();
    if (raw instanceof Double d) return d.intValue();
    if (raw instanceof Float f) return f.intValue();
    if (raw instanceof String s) return Integer.parseInt(s);
    if (raw instanceof Boolean b) return b ? 1 : 0;
    throw convError(raw, "Integer", name);
  }

  private static Long toLong(Object raw, String name) {
    if (raw instanceof Long l) return l;
    if (raw instanceof Integer i) return i.longValue();
    if (raw instanceof Double d) return d.longValue();
    if (raw instanceof Float f) return (long) f.floatValue();
    if (raw instanceof String s) return Long.parseLong(s);
    if (raw instanceof Boolean b) return b ? 1L : 0L;
    throw convError(raw, "Long", name);
  }

  private static Double toDouble(Object raw, String name) {
    if (raw instanceof Double d) return d;
    if (raw instanceof Float f) return f.doubleValue();
    if (raw instanceof Integer i) return i.doubleValue();
    if (raw instanceof Long l) return l.doubleValue();
    if (raw instanceof String s) return Double.parseDouble(s);
    if (raw instanceof Boolean b) return b ? 1d : 0d;
    throw convError(raw, "Double", name);
  }

  private static Float toFloat(Object raw, String name) {
    if (raw instanceof Float f) return f;
    if (raw instanceof Double d) return d.floatValue();
    if (raw instanceof Integer i) return i.floatValue();
    if (raw instanceof Long l) return l.floatValue();
    if (raw instanceof String s) return Float.parseFloat(s);
    if (raw instanceof Boolean b) return b ? 1f : 0f;
    throw convError(raw, "Float", name);
  }

  private static Boolean toBoolean(Object raw, String name) {
    if (raw instanceof Boolean b) return b;
    if (raw instanceof Integer i) return i != 0;
    if (raw instanceof Long l) return l != 0L;
    if (raw instanceof String s) return Boolean.parseBoolean(s);
    throw convError(raw, "Boolean", name);
  }

  private static Byte toByte(Object raw, String name) {
    if (raw instanceof Byte b) return b;
    if (raw instanceof Integer i) return i.byteValue();
    if (raw instanceof Long l) return l.byteValue();
    if (raw instanceof String s) return Byte.parseByte(s);
    throw convError(raw, "Byte", name);
  }

  private static Short toShort(Object raw, String name) {
    if (raw instanceof Short s) return s;
    if (raw instanceof Integer i) return i.shortValue();
    if (raw instanceof Long l) return l.shortValue();
    if (raw instanceof String s) return Short.parseShort(s);
    throw convError(raw, "Short", name);
  }

  private static Character toChar(Object raw, String name) {
    if (raw instanceof Character c) return c;
    if (raw instanceof String s && s.length() == 1) return s.charAt(0);
    throw convError(raw, "Character", name);
  }

  private static IllegalArgumentException convError(Object raw, String to, String name) {
    return new IllegalArgumentException("Cannot convert column '" + name + "' of type "
        + raw.getClass().getSimpleName() + " to " + to);
  }

  private static Object introspect(Class<?> type) {
    if (type.isRecord()) {
      try {
        // Record: use canonical constructor
        // JDK guarantees a canonical constructor with all components in order
        var components = type.getRecordComponents();
        Class<?>[] paramTypes = Arrays.stream(components).map(c -> c.getType()).toArray(Class[]::new);
        Constructor<?> ctor = type.getDeclaredConstructor(paramTypes);
        if (!ctor.canAccess(null)) ctor.setAccessible(true);
        String[] names = Arrays.stream(components).map(c -> c.getName()).toArray(String[]::new);
        Class<?>[] optionalInners = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
          optionalInners[i] = resolveOptionalInner(components[i].getGenericType());
        }
        return new RecordMeta(ctor, names, paramTypes, optionalInners);
      } catch (Exception e) {
        throw new IllegalArgumentException("Failed to introspect record type: " + type.getName(), e);
      }
    } else {
      try {
        Constructor<?> noArg = type.getDeclaredConstructor();
        if (!Modifier.isPublic(noArg.getModifiers())) noArg.setAccessible(true);

        Map<String, Method> setters = new HashMap<>();
        Map<String, Field> fields = new HashMap<>();
        Map<String, Class<?>> optInnerSetter = new HashMap<>();
        Map<String, Class<?>> optInnerField = new HashMap<>();

        for (Method m : type.getMethods()) { // public methods only (including inherited)
          if (isSetter(m)) {
            String prop = setterPropertyName(m).toLowerCase(Locale.ROOT);
            setters.put(prop, m);
            // capture Optional inner for setter parameter
            Type gt = m.getGenericParameterTypes()[0];
            Class<?> inner = resolveOptionalInner(gt);
            if (inner != null) optInnerSetter.put(prop, inner);
          }
        }
        Class<?> cls = type;
        while (cls != null && cls != Object.class) {
          for (Field f : cls.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            String name = f.getName().toLowerCase(Locale.ROOT);
            fields.putIfAbsent(name, f);
            Class<?> inner = resolveOptionalInner(f.getGenericType());
            if (inner != null) optInnerField.put(name, inner);
          }
          cls = cls.getSuperclass();
        }
        return new PojoMeta(noArg, setters, fields, optInnerSetter, optInnerField);
      } catch (NoSuchMethodException nsme) {
        throw new IllegalArgumentException("POJO type must have a no-arg constructor: " + type.getName());
      }
    }
  }

  private static boolean isSetter(Method m) {
    return Modifier.isPublic(m.getModifiers())
        && m.getName().startsWith("set")
        && m.getParameterCount() == 1;
  }

  private static String setterPropertyName(Method m) {
    String name = m.getName().substring(3); // after 'set'
    if (name.isEmpty()) return name;
    // uncapitalize first letter
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }

  private static Class<?> resolveOptionalInner(Type genericType) {
    if (genericType instanceof ParameterizedType pt) {
      Type raw = pt.getRawType();
      if (raw instanceof Class<?> rawCls && Optional.class.isAssignableFrom(rawCls)) {
        Type[] args = pt.getActualTypeArguments();
        if (args.length == 1) {
          Type arg = args[0];
          if (arg instanceof Class<?>) return (Class<?>) arg;
          // Best-effort: if it's itself parameterized, take raw type
          if (arg instanceof ParameterizedType p2 && p2.getRawType() instanceof Class<?> c2) return c2;
        }
      }
    }
    if (genericType instanceof Class<?> c && Optional.class.isAssignableFrom(c)) {
      // Optional without generics info (raw type) -> unknown inner
      return null;
    }
    return null;
  }
}
