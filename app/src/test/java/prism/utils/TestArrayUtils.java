package prism.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ArrayUtils Unit Tests")
class TestArrayUtils {

    @Nested
    @DisplayName("splitCommand")
    class SplitCommandTests {

        @Test
        @DisplayName("should return empty array when command is null")
        void shouldReturnEmptyArrayWhenCommandIsNull() {
            String[] result = ArrayUtils.splitCommand(null);
            assertArrayEquals(new String[0], result);
        }

        @Test
        @DisplayName("should return empty array when command is empty string")
        void shouldReturnEmptyArrayWhenCommandIsEmpty() {
            String[] result = ArrayUtils.splitCommand("");
            assertArrayEquals(new String[0], result);
        }

        @Test
        @DisplayName("should return empty array when command contains only whitespace")
        void shouldReturnEmptyArrayWhenCommandIsWhitespace() {
            String[] result = ArrayUtils.splitCommand("   \t  \n ");
            assertArrayEquals(new String[0], result);
        }

        @Test
        @DisplayName("should split single word command")
        void shouldSplitSingleWordCommand() {
            String[] result = ArrayUtils.splitCommand("status");
            assertArrayEquals(new String[]{"status"}, result);
        }

        @Test
        @DisplayName("should split multi-word command by whitespace")
        void shouldSplitMultiWordCommandByWhitespace() {
            String[] result = ArrayUtils.splitCommand("git checkout main");
            assertArrayEquals(new String[]{"git", "checkout", "main"}, result);
        }

        @Test
        @DisplayName("should handle multiple consecutive whitespace characters between tokens")
        void shouldHandleMultipleConsecutiveWhitespaceCharacters() {
            String[] result = ArrayUtils.splitCommand("run    --verbose     task");
            assertArrayEquals(new String[]{"run", "--verbose", "task"}, result);
        }

        @Test
        @DisplayName("should handle leading and trailing whitespace")
        void shouldHandleLeadingAndTrailingWhitespace() {
            String[] result = ArrayUtils.splitCommand("  prism build  ");
            assertArrayEquals(new String[]{"prism", "build"}, result);
        }

        @Test
        @DisplayName("should preserve double-quoted token and strip enclosing double quotes")
        void shouldPreserveDoubleQuotedTokenAndStripQuotes() {
            String[] result = ArrayUtils.splitCommand("git commit -m \"initial commit\"");
            assertArrayEquals(new String[]{"git", "commit", "-m", "initial commit"}, result);
        }

        @Test
        @DisplayName("should preserve single-quoted token and strip enclosing single quotes")
        void shouldPreserveSingleQuotedTokenAndStripQuotes() {
            String[] result = ArrayUtils.splitCommand("sh -c 'echo hello world'");
            assertArrayEquals(new String[]{"sh", "-c", "echo hello world"}, result);
        }

        @Test
        @DisplayName("should handle mixed single and double quoted tokens in same command")
        void shouldHandleMixedSingleAndDoubleQuotedTokens() {
            String[] result = ArrayUtils.splitCommand("app --msg 'hello there' --name \"test user\" flag");
            assertArrayEquals(new String[]{"app", "--msg", "hello there", "--name", "test user", "flag"}, result);
        }

        @Test
        @DisplayName("should handle empty double quotes as an empty token")
        void shouldHandleEmptyDoubleQuotesAsEmptyToken() {
            String[] result = ArrayUtils.splitCommand("cmd \"\"");
            assertArrayEquals(new String[]{"cmd", ""}, result);
        }

        @Test
        @DisplayName("should handle empty single quotes as an empty token")
        void shouldHandleEmptySingleQuotesAsEmptyToken() {
            String[] result = ArrayUtils.splitCommand("cmd ''");
            assertArrayEquals(new String[]{"cmd", ""}, result);
        }
    }

    @Nested
    @DisplayName("add")
    class AddTests {

        @Test
        @DisplayName("should throw IllegalArgumentException when source array is null")
        void shouldThrowIllegalArgumentExceptionWhenSourceArrayIsNull() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ArrayUtils.add(null, "element")
            );
            assertEquals("Array to add item can not be empty", exception.getMessage());
        }

        @Test
        @DisplayName("should add element to empty array")
        void shouldAddElementToEmptyArray() {
            String[] initial = new String[0];
            String[] result = ArrayUtils.add(initial, "first");
            assertArrayEquals(new String[]{"first"}, result);
        }

        @Test
        @DisplayName("should append element to end of non-empty array")
        void shouldAppendElementToEndOfNonEmptyArray() {
            String[] initial = new String[]{"first", "second"};
            String[] result = ArrayUtils.add(initial, "third");
            assertArrayEquals(new String[]{"first", "second", "third"}, result);
        }

        @Test
        @DisplayName("should preserve array element type with custom objects")
        void shouldPreserveArrayElementTypeWithCustomObjects() {
            Integer[] initial = new Integer[]{1, 2, 3};
            Integer[] result = ArrayUtils.add(initial, 4);
            assertArrayEquals(new Integer[]{1, 2, 3, 4}, result);
        }

        @Test
        @DisplayName("should append null value to array")
        void shouldAppendNullValueToArray() {
            String[] initial = new String[]{"a", "b"};
            String[] result = ArrayUtils.add(initial, null);
            assertArrayEquals(new String[]{"a", "b", null}, result);
        }
    }

    @Nested
    @DisplayName("get")
    class GetTests {

        @Test
        @DisplayName("should return null when map array is null")
        void shouldReturnNullWhenMapArrayIsNull() {
            String result = ArrayUtils.get("key", null);
            assertNull(result);
        }

        @Test
        @DisplayName("should return null when map array is empty")
        void shouldReturnNullWhenMapArrayIsEmpty() {
            String result = ArrayUtils.get("key", new String[0]);
            assertNull(result);
        }

        @Test
        @DisplayName("should return matching element when key is present at start")
        void shouldReturnMatchingElementWhenKeyIsAtStart() {
            String[] array = new String[]{"alpha", "beta", "gamma"};
            String result = ArrayUtils.get("alpha", array);
            assertEquals("alpha", result);
        }

        @Test
        @DisplayName("should return matching element when key is present in middle")
        void shouldReturnMatchingElementWhenKeyIsInMiddle() {
            String[] array = new String[]{"alpha", "beta", "gamma"};
            String result = ArrayUtils.get("beta", array);
            assertEquals("beta", result);
        }

        @Test
        @DisplayName("should return matching element when key is present at end")
        void shouldReturnMatchingElementWhenKeyIsAtEnd() {
            String[] array = new String[]{"alpha", "beta", "gamma"};
            String result = ArrayUtils.get("gamma", array);
            assertEquals("gamma", result);
        }

        @Test
        @DisplayName("should return null when key is not found in array")
        void shouldReturnNullWhenKeyIsNotFound() {
            String[] array = new String[]{"alpha", "beta"};
            String result = ArrayUtils.get("delta", array);
            assertNull(result);
        }

        @Test
        @DisplayName("should return first match when array contains duplicate keys")
        void shouldReturnFirstMatchWhenDuplicatesExist() {
            String first = new String("dup");
            String second = new String("dup");
            String[] array = new String[]{first, second};
            String result = ArrayUtils.get("dup", array);
            assertEquals("dup", result);
        }
    }

    @Nested
    @DisplayName("mapGet")
    class MapGetTests {

        @Test
        @DisplayName("should return null when map array is null")
        void shouldReturnNullWhenMapArrayIsNull() {
            String result = ArrayUtils.mapGet("key", null);
            assertNull(result);
        }

        @Test
        @DisplayName("should return null when map array is empty")
        void shouldReturnNullWhenMapArrayIsEmpty() {
            String result = ArrayUtils.mapGet("key", new String[0]);
            assertNull(result);
        }

        @Test
        @DisplayName("should return value for key at first pair")
        void shouldReturnValueForKeyAtFirstPair() {
            String[] pairs = new String[]{"k1", "v1", "k2", "v2"};
            String result = ArrayUtils.mapGet("k1", pairs);
            assertEquals("v1", result);
        }

        @Test
        @DisplayName("should return value for key at subsequent pair")
        void shouldReturnValueForKeyAtSubsequentPair() {
            String[] pairs = new String[]{"k1", "v1", "k2", "v2", "k3", "v3"};
            String result = ArrayUtils.mapGet("k3", pairs);
            assertEquals("v3", result);
        }

        @Test
        @DisplayName("should return null when key is not found in map array")
        void shouldReturnNullWhenKeyIsNotFound() {
            String[] pairs = new String[]{"k1", "v1", "k2", "v2"};
            String result = ArrayUtils.mapGet("k99", pairs);
            assertNull(result);
        }

        @Test
        @DisplayName("should not match key that appears only at odd value index")
        void shouldNotMatchKeyThatAppearsOnlyAsValue() {
            String[] pairs = new String[]{"k1", "target", "k2", "v2"};
            String result = ArrayUtils.mapGet("target", pairs);
            assertNull(result);
        }

        @Test
        @DisplayName("should return first value when duplicate keys exist")
        void shouldReturnFirstValueWhenDuplicateKeysExist() {
            String[] pairs = new String[]{"dup", "first", "dup", "second"};
            String result = ArrayUtils.mapGet("dup", pairs);
            assertEquals("first", result);
        }

        @Test
        @DisplayName("should return null when value associated with key is null")
        void shouldReturnNullWhenAssociatedValueIsNull() {
            String[] pairs = new String[]{"k1", null, "k2", "v2"};
            String result = ArrayUtils.mapGet("k1", pairs);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("contains")
    class ContainsTests {

        @Test
        @DisplayName("should return false when array is null")
        void shouldReturnFalseWhenArrayIsNull() {
            assertFalse(ArrayUtils.contains(null, "item"));
        }

        @Test
        @DisplayName("should return false when array is empty")
        void shouldReturnFalseWhenArrayIsEmpty() {
            assertFalse(ArrayUtils.contains(new Object[0], "item"));
        }

        @Test
        @DisplayName("should return false when search value is null")
        void shouldReturnFalseWhenSearchValueIsNull() {
            assertFalse(ArrayUtils.contains(new Object[]{"a", "b"}, null));
        }

        @Test
        @DisplayName("should return false when both array and value are null")
        void shouldReturnFalseWhenBothArrayAndValueAreNull() {
            assertFalse(ArrayUtils.contains(null, null));
        }

        @Test
        @DisplayName("should return true when value is found at first index")
        void shouldReturnTrueWhenValueIsAtFirstIndex() {
            assertTrue(ArrayUtils.contains(new String[]{"target", "other"}, "target"));
        }

        @Test
        @DisplayName("should return true when value is found at middle index")
        void shouldReturnTrueWhenValueIsInMiddle() {
            assertTrue(ArrayUtils.contains(new String[]{"a", "target", "b"}, "target"));
        }

        @Test
        @DisplayName("should return true when value is found at last index")
        void shouldReturnTrueWhenValueIsAtLastIndex() {
            assertTrue(ArrayUtils.contains(new String[]{"a", "b", "target"}, "target"));
        }

        @Test
        @DisplayName("should return false when value is not present in array")
        void shouldReturnFalseWhenValueIsNotPresent() {
            assertFalse(ArrayUtils.contains(new String[]{"a", "b", "c"}, "z"));
        }

        @Test
        @DisplayName("should handle array containing null elements safely without throwing exception")
        void shouldHandleArrayContainingNullElementsSafely() {
            assertFalse(ArrayUtils.contains(new Object[]{null, "other"}, "target"));
        }
    }

    @Nested
    @DisplayName("isEmpty")
    class IsEmptyTests {

        @Test
        @DisplayName("should return true when array is null")
        void shouldReturnTrueWhenArrayIsNull() {
            assertTrue(ArrayUtils.isEmpty(null));
        }

        @Test
        @DisplayName("should return true when array is empty")
        void shouldReturnTrueWhenArrayIsEmpty() {
            assertTrue(ArrayUtils.isEmpty(new Object[0]));
        }

        @Test
        @DisplayName("should return false when array has one element")
        void shouldReturnFalseWhenArrayHasOneElement() {
            assertFalse(ArrayUtils.isEmpty(new Object[]{"element"}));
        }

        @Test
        @DisplayName("should return false when array has multiple elements")
        void shouldReturnFalseWhenArrayHasMultipleElements() {
            assertFalse(ArrayUtils.isEmpty(new Object[]{"a", "b", "c"}));
        }

        @Test
        @DisplayName("should return false when array has null element but length > 0")
        void shouldReturnFalseWhenArrayContainsNullElement() {
            assertFalse(ArrayUtils.isEmpty(new Object[]{null}));
        }
    }
}