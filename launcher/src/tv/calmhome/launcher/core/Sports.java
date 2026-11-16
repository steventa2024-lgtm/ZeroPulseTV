package tv.calmhome.launcher.core;

/**
 * Sports from guide metadata only (pure Java, unit-tested). A programme is a sports event only when the guide's own
 * category says so; the sport is read from that category, never guessed from titles. Nothing is added, and
 * availability is just "on air now / later / over" on the user's own channels.
 */
public final class Sports {

    public enum Sport {
        SOCCER("Soccer"), BASKETBALL("Basketball"), AMERICAN_FOOTBALL("American Football"), BASEBALL("Baseball"),
        MOTORSPORT("Motorsport"), COMBAT("Combat Sports"), TENNIS("Tennis"), OTHER("Other");

        public final String label;

        Sport(String label) {
            this.label = label;
        }
    }

    public enum State { LIVE, UPCOMING, FINISHED }

    private Sports() { }

    /** The sport named by an XMLTV category, OTHER for a generic sports category, or null if it isn't sport. */
    public static Sport of(String category) {
        String c = " " + ChannelFilter.fold(category).replaceAll("[^a-z]+", " ") + " ";
        if (c.trim().isEmpty()) return null;
        if (c.contains(" american football ") || c.contains(" nfl ")) return Sport.AMERICAN_FOOTBALL;
        if (c.contains(" soccer ") || c.contains(" football ") || c.contains(" futbol ") || c.contains(" fussball ")
                || c.contains(" calcio ")) return Sport.SOCCER;
        if (c.contains(" basketball ") || c.contains(" nba ")) return Sport.BASKETBALL;
        if (c.contains(" baseball ") || c.contains(" mlb ")) return Sport.BASEBALL;
        if (c.contains(" motorsport ") || c.contains(" motor sport ") || c.contains(" motorsports ") || c.contains(" formula ")
                || c.contains(" racing ") || c.contains(" nascar ") || c.contains(" motogp ") || c.contains(" rally ")) return Sport.MOTORSPORT;
        if (c.contains(" boxing ") || c.contains(" mma ") || c.contains(" ufc ") || c.contains(" wrestling ")
                || c.contains(" martial arts ") || c.contains(" kickboxing ")) return Sport.COMBAT;
        if (c.contains(" tennis ")) return Sport.TENNIS;
        if (c.contains(" sport ") || c.contains(" sports ") || c.contains(" deportes ") || c.contains(" sportif ")) return Sport.OTHER;
        return null;
    }

    public static State state(long now, long start, long stop) {
        if (now >= stop) return State.FINISHED;
        return now >= start ? State.LIVE : State.UPCOMING;
    }
}
