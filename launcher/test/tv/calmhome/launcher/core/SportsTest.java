package tv.calmhome.launcher.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import tv.calmhome.launcher.core.Sports.Sport;
import tv.calmhome.launcher.core.Sports.State;

public class SportsTest {

    @Test public void sportComesFromTheGuideCategory() {
        assertEquals(Sport.SOCCER, Sports.of("Football"));
        assertEquals(Sport.SOCCER, Sports.of("Fútbol"));
        assertEquals(Sport.AMERICAN_FOOTBALL, Sports.of("American Football"));
        assertEquals(Sport.BASKETBALL, Sports.of("Sports / Basketball"));
        assertEquals(Sport.BASEBALL, Sports.of("baseball"));
        assertEquals(Sport.MOTORSPORT, Sports.of("Motor Sport"));
        assertEquals(Sport.MOTORSPORT, Sports.of("Formula 1"));
        assertEquals(Sport.COMBAT, Sports.of("Boxing"));
        assertEquals(Sport.TENNIS, Sports.of("Tennis"));
        assertEquals(Sport.OTHER, Sports.of("Sports"));
        assertEquals(Sport.OTHER, Sports.of("Sport event"));
    }

    @Test public void nonSportCategoriesAreNotEvents() {
        assertNull(Sports.of("News"));
        assertNull(Sports.of("Movie"));
        assertNull(Sports.of("Transport"));       // contains "sport" only inside another word
        assertNull(Sports.of(""));
        assertNull(Sports.of(null));
    }

    @Test public void stateFromTimes() {
        assertEquals(State.UPCOMING, Sports.state(100, 200, 300));
        assertEquals(State.LIVE, Sports.state(200, 200, 300));
        assertEquals(State.FINISHED, Sports.state(300, 200, 300));
    }
}
