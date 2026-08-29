package club.code2create.mcremote.examples;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AxisFlatTest {

    @Test
    void horizontalNegativeRegionIsDashedAndPositiveRegionIsContinuous() {
        assertTrue(AxisFlat.isVisibleAxisPosition(-40));
        assertFalse(AxisFlat.isVisibleAxisPosition(-39));
        assertTrue(AxisFlat.isVisibleAxisPosition(-2));
        assertFalse(AxisFlat.isVisibleAxisPosition(-1));
        assertTrue(AxisFlat.isVisibleAxisPosition(0));
        assertTrue(AxisFlat.isVisibleAxisPosition(1));
        assertTrue(AxisFlat.isVisibleAxisPosition(40));
    }

    @Test
    void verticalNegativeRegionStartsAt63DashedAndBecomesContinuousAt96() {
        assertTrue(AxisFlat.isVisibleYAxisPosition(63));
        assertFalse(AxisFlat.isVisibleYAxisPosition(64));
        assertTrue(AxisFlat.isVisibleYAxisPosition(65));
        assertTrue(AxisFlat.isVisibleYAxisPosition(95));
        assertTrue(AxisFlat.isVisibleYAxisPosition(96));
        assertTrue(AxisFlat.isVisibleYAxisPosition(97));
        assertTrue(AxisFlat.isVisibleYAxisPosition(127));
    }
}
