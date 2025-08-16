package db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.mimir.RowBinder;
import org.ruitx.jaws.types.Row;

public class RowBinderTest {

  private static Row rowOf(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return new Row(m);
  }

  @Test
  @DisplayName("Record mapping with Optionals and conversions (int->boolean, case-insensitive)")
  void recordMappingOptionalsAndConversions() {
    Row row = rowOf(
        "ID", 123,                   // Integer -> Long Optional
        "user", "alice",
        "active", 1                  // 1 -> true
    );
    UserRec rec = RowBinder.map(row, UserRec.class);
    assertEquals(123L, rec.id().orElseThrow());
    assertEquals("alice", rec.user().orElseThrow());
    assertTrue(rec.active().orElseThrow());
  }

  @Test
  @DisplayName("Record mapping with numeric conversion (Long->Integer)")
  void recordNumericConversion() {
    Row row = rowOf("count", 42L);
    CountRec rec = RowBinder.map(row, CountRec.class);
    assertEquals(42, rec.count().orElseThrow());
  }

  @Test
  @DisplayName("Record missing non-Optional reference should throw")
  void recordMissingNonOptionalThrows() {
    Row row = rowOf();
    assertThrows(IllegalArgumentException.class, () -> RowBinder.map(row, NonOptionalRec.class));
  }

  @Test
  @DisplayName("POJO mapping with Optional, setter, and primitive default")
  void pojoMappingOptionalSetterPrimitiveDefault() {
    Row row = rowOf("ID", 7L, "name", "Bob");
    UserPojo pojo = RowBinder.map(row, UserPojo.class);
    assertEquals(7L, pojo.getId().orElseThrow());
    assertEquals("Bob", pojo.getName());
    assertEquals(0, pojo.getAge()); // default primitive when missing
  }

  @Test
  @DisplayName("POJO missing required non-Optional reference should throw")
  void pojoMissingRequiredRefThrows() {
    Row row = rowOf("ID", 7L); // name missing
    assertThrows(IllegalArgumentException.class, () -> RowBinder.map(row, UserPojo.class));
  }

  @Test
  @DisplayName("Case-insensitive alias matching and boolean from String")
  void caseInsensitiveAndBooleanFromString() {
    Row row = rowOf("Id", 5, "UsEr", "Carol", "AcTiVe", "true");
    UserRec rec = RowBinder.map(row, UserRec.class);
    assertEquals(5L, rec.id().orElseThrow());
    assertEquals("Carol", rec.user().orElseThrow());
    assertTrue(rec.active().orElseThrow());
  }

  @Test
  @DisplayName("Optional<String> from numeric should convert via toString")
  void optionalStringFromNumeric() {
    record SRec(Optional<String> value) {

    }
    Row row = rowOf("value", 123);
    SRec rec = RowBinder.map(row, SRec.class);
    assertEquals("123", rec.value().orElseThrow());
  }

  @Test
  @DisplayName("POJO Optional<Boolean> from 0/1 and from string true/false")
  void pojoOptionalBooleanFromNumericAndString() {
    Row row1 = rowOf("active", 1);
    BoolPojo p1 = RowBinder.map(row1, BoolPojo.class);
    assertTrue(p1.getActive().orElseThrow());

    Row row2 = rowOf("active", "false");
    BoolPojo p2 = RowBinder.map(row2, BoolPojo.class);
    assertFalse(p2.getActive().orElseThrow());
  }

  @Test
  @DisplayName("Unsupported conversion should throw (byte[] -> Long)")
  void unsupportedConversionThrows() {
    record LRec(Optional<Long> id) {

    }
    Row row = rowOf("id", new byte[]{1, 2, 3});
    assertThrows(IllegalArgumentException.class, () -> RowBinder.map(row, LRec.class));
  }

  @Test
  @DisplayName("Field-only POJO Optional mapping without setter")
  void fieldOnlyPojoOptionalMapping() {
    Row row = rowOf("nickname", "Neo");
    FieldOnlyPojo p = RowBinder.map(row, FieldOnlyPojo.class);
    assertEquals("Neo", p.nickname.orElseThrow());
  }

  @Test
  @DisplayName("Optional boxed conversions: numeric<->numeric, string, boolean, and byte[] passthrough")
  void boxedOptionalsConversions() {
    byte[] arr = new byte[]{9, 8, 7};
    Row row = rowOf(
        "i", 123L,       // Long -> Integer
        "l", true,       // Boolean -> Long (1L)
        "d", "3.5",     // String -> Double
        "f", 2,          // Integer -> Float
        "b", 0L,         // Long -> Boolean (false)
        "s", 42,         // Integer -> String ("42")
        "bytes", arr     // byte[] -> byte[]
    );
    BoxedOptionals rec = RowBinder.map(row, BoxedOptionals.class);
    assertEquals(123, rec.i().orElseThrow());
    assertEquals(1L, rec.l().orElseThrow());
    assertEquals(3.5d, rec.d().orElseThrow());
    assertEquals(2.0f, rec.f().orElseThrow());
    assertFalse(rec.b().orElseThrow());
    assertEquals("42", rec.s().orElseThrow());
    assertEquals(arr, rec.bytes().orElseThrow());
  }

  @Test
  @DisplayName("Primitive targets: from strings and other numerics, boolean from int, char from 1-char string")
  void primitivesConversions() {
    Row row = rowOf(
        "i", "7",      // String -> int
        "l", 9,         // Integer -> long
        "d", 1.5f,      // Float -> double
        "f", 3L,        // Long -> float
        "b", 0,         // Integer -> boolean (false)
        "c", "Z",       // String(1) -> char
        "s", "12",      // String -> short
        "by", "5"       // String -> byte
    );
    Primitives p = RowBinder.map(row, Primitives.class);
    assertEquals(7, p.i());
    assertEquals(9L, p.l());
    assertEquals(1.5d, p.d());
    assertEquals(3.0f, p.f());
    assertFalse(p.b());
    assertEquals('Z', p.c());
    assertEquals((short) 12, p.s());
    assertEquals((byte) 5, p.by());
  }

  @Test
  @DisplayName("Char from invalid string length should throw")
  void charFromInvalidStringThrows() {
    record CRec(char c) {

    }
    Row row = rowOf("c", "TooLong");
    assertThrows(IllegalArgumentException.class, () -> RowBinder.map(row, CRec.class));
  }

  @Test
  @DisplayName("Boolean parsing from mixed cases in string")
  void booleanParsingCaseInsensitive() {
    record BRec(Optional<Boolean> b) {

    }
    Row row = rowOf("b", "TrUe");
    BRec rec = RowBinder.map(row, BRec.class);
    assertTrue(rec.b().orElseThrow());
  }

  @Test
  @DisplayName("Nested Optional<Optional<T>> is not supported and should throw")
  void nestedOptionalShouldThrow() {
    record R(Optional<Optional<Long>> v) {

    }
    Row row = rowOf("v", 1);
    assertThrows(IllegalArgumentException.class, () -> RowBinder.map(row, R.class));
  }

  @Test
  @DisplayName("Raw Optional field wraps raw value without conversion")
  void rawOptionalFieldWrapsRaw() {
    Row row = rowOf("value", 123L);
    RawOptPojo p = RowBinder.map(row, RawOptPojo.class);
    assertTrue(p.value.isPresent());
    assertEquals(123L, p.value.get());
  }

  @Test
  @DisplayName("Optional<Double> from String numeric")
  void optionalDoubleFromString() {
    record DRec(Optional<Double> d) {

    }
    Row row = rowOf("d", "2.75");
    DRec rec = RowBinder.map(row, DRec.class);
    assertEquals(2.75, rec.d().orElseThrow());
  }

  @Test
  @DisplayName("Record with missing primitive fields should default to zero/false")
  void recordMissingPrimitiveDefaults() {
    record PrimRec(int i, long l, boolean b) {

    }
    Row row = rowOf();
    PrimRec rec = RowBinder.map(row, PrimRec.class);
    assertEquals(0, rec.i());
    assertEquals(0L, rec.l());
    assertFalse(rec.b());
  }

  @Test
  @DisplayName("POJO with missing primitive fields should default to zero/false")
  void pojoMissingPrimitiveDefaults() {
    Row row = rowOf();
    PrimPojo p = RowBinder.map(row, PrimPojo.class);
    assertEquals(0, p.getI());
    assertEquals(0L, p.getL());
    assertFalse(p.isB());
  }

  public static class RawOptPojo {

    public Optional value; // raw Optional (no generic)
  }

  public static class PrimPojo {

    private int i;
    private long l;
    private boolean b;

    public PrimPojo() {
    }

    public int getI() {
      return i;
    }

    public void setI(int i) {
      this.i = i;
    }

    public long getL() {
      return l;
    }

    public void setL(long l) {
      this.l = l;
    }

    public boolean isB() {
      return b;
    }

    public void setB(boolean b) {
      this.b = b;
    }
  }

  public static class BoolPojo {

    private Optional<Boolean> active;

    public BoolPojo() {
    }

    public Optional<Boolean> getActive() {
      return active;
    }

    public void setActive(Optional<Boolean> active) {
      this.active = active;
    }
  }

  public static class FieldOnlyPojo {

    public Optional<String> nickname; // no setter
  }

  // --- Test projection records ---
  record UserRec(Optional<Long> id, Optional<String> user, Optional<Boolean> active) {

  }

  record CountRec(Optional<Integer> count) {

  }

  record NonOptionalRec(String user) {

  }

  // --- Test POJO types ---
  public static class UserPojo {

    private Optional<Long> id;
    private String name;
    private int age; // primitive default expected when missing

    public UserPojo() {
    }

    public Optional<Long> getId() {
      return id;
    }

    public void setId(Optional<Long> id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public int getAge() {
      return age;
    }

    public void setAge(int age) {
      this.age = age;
    }
  }

  // --- Additional comprehensive conversion coverage ---
  record BoxedOptionals(
      Optional<Integer> i,
      Optional<Long> l,
      Optional<Double> d,
      Optional<Float> f,
      Optional<Boolean> b,
      Optional<String> s,
      Optional<byte[]> bytes
  ) {

  }

  record Primitives(int i, long l, double d, float f, boolean b, char c, short s, byte by) {

  }
}
