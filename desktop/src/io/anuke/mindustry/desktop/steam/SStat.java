package io.anuke.mindustry.desktop.steam;

public enum SStat{
    unitsDestroyed,
    attacksWon,
    pvpsWon,
    timesLaunched,
    zoneMechsUsed,
    blocksDestroyed,
    itemsLaunched,
    reactorsOverheated,
    maxUnitActive,
    unitsBuilt,
    bossesDefeated,
    maxPlayersServer,
    mapsMade,
    mapsPublished,
    maxWavesSurvived,
    blocksBuilt,
    ;

    public int get(){
        return SVarsUtil.stats.stats.getStatI(name(), 0);
    }

    public void max(int amount){
        if(amount > get()){
            add(amount - get());
        }
    }

    public void add(int amount){
        SVarsUtil.stats.stats.setStatI(name(), get() + amount);
        SVarsUtil.stats.onUpdate();

        for(SAchievement a : SAchievement.ALL_ACHIEVEMENTS){
            a.checkCompletion();
        }
    }

    public void add(){
        add(1);
    }
}
