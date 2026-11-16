package tv.calmhome.launcher.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import org.junit.Test;

import tv.calmhome.launcher.core.Features.Module;
import tv.calmhome.launcher.core.Features.Section;

public class FeaturesTest {

    private static Features.State state(final Set<Module> on, final Set<Section> sections) {
        return new Features.State() {
            @Override public boolean enabled(Module m) { return on.contains(m); }
            @Override public boolean sectionEnabled(Section s) { return sections.contains(s); }
        };
    }

    @Test public void nothingWhenBothOff() {
        Features.State s = state(EnumSet.noneOf(Module.class), EnumSet.allOf(Section.class));
        assertTrue(Features.homeEntries(s).isEmpty());
        assertTrue(Features.sections(s).isEmpty());
        assertFalse(Features.mayOpen(s, Module.STREAMING));
        assertFalse(Features.mayOpen(s, Module.WEB));
        assertFalse(Features.mayOpen(s, Section.LIVE_TV));
    }

    @Test public void togglesAreIndependent() {
        Features.State web = state(EnumSet.of(Module.WEB), EnumSet.allOf(Section.class));
        assertEquals(Arrays.asList(Module.WEB), Features.homeEntries(web));
        assertTrue(Features.mayOpen(web, Module.WEB));
        assertFalse(Features.mayOpen(web, Module.STREAMING));
        assertTrue(Features.sections(web).isEmpty());

        Features.State streaming = state(EnumSet.of(Module.STREAMING), EnumSet.allOf(Section.class));
        assertEquals(Arrays.asList(Module.STREAMING), Features.homeEntries(streaming));
        assertFalse(Features.mayOpen(streaming, Module.WEB));

        Features.State both = state(EnumSet.allOf(Module.class), EnumSet.allOf(Section.class));
        assertEquals(Arrays.asList(Module.STREAMING, Module.WEB), Features.homeEntries(both));
    }

    @Test public void sectionsFollowTheirSwitchesInOrder() {
        Features.State s = state(EnumSet.of(Module.STREAMING), EnumSet.of(Section.SPORTS, Section.LIVE_TV));
        assertEquals(Arrays.asList(Section.LIVE_TV, Section.SPORTS), Features.sections(s));
        assertTrue(Features.mayOpen(s, Section.SPORTS));
        assertFalse(Features.mayOpen(s, Section.MOVIES));
    }

    @Test public void preferenceKeysAreStable() {
        // Saved in the user's preferences: renaming one would silently reset that switch.
        assertEquals("live movies shows sports", Section.LIVE_TV.key + " " + Section.MOVIES.key + " "
                + Section.SHOWS.key + " " + Section.SPORTS.key);
    }
}
