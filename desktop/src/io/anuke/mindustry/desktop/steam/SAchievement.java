package io.anuke.mindustry.desktop.steam;

public enum SAchievement{
    completeTutorial,
    kill1kEnemies(SStat.unitsDestroyed, 1000),
    kill100kEnemies(SStat.unitsDestroyed, 1000 * 100),
    launch10kItems(SStat.itemsLaunched, 1000 * 10),
    launch1milItems(SStat.itemsLaunched, 1000 * 1000),
    win10Attack(SStat.attacksWon, 10),
    win10PvP(SStat.pvpsWon, 10),
    defeatAttack5Waves,
    launch30Times(SStat.timesLaunched, 30),
    survive100Waves(SStat.maxWavesSurvived, 100),
    survive500Waves(SStat.maxWavesSurvived, 500),
    researchAll,
    useAllMechs(SStat.zoneMechsUsed, 6),
    shockWetEnemy,
    killEnemyPhaseWall,
    researchRouter,
    place10kBlocks(SStat.blocksBuilt, 10 * 1000),
    destroy1kBlocks(SStat.blocksDestroyed, 1000),
    overheatReactor(SStat.reactorsOverheated, 1),
    make10maps(SStat.mapsMade, 10),
    downloadMapWorkshop,
    publishMap(SStat.mapsPublished, 1),
    defeatBoss(SStat.bossesDefeated, 1),
    unlockAllZones,
    configAllZones,
    drop10kitems,
    powerupImpactReactor,
    obtainThorium,
    obtainTitanium,
    suicideBomb,
    buildDaggerFactory,
    issueAttackCommand,
    active100Units(SStat.maxUnitActive, 100),
    active10Phantoms,
    active50Crawlers,
    build1000Units,
    earnSRank,
    earnSSRank,
    dieExclusion,
    drown,
    fillCoreAllCampaign,
    hostServer10(SStat.maxPlayersServer, 10),
    buildMeltdownSpectre,
    launchItemPad,
    skipLaunching2Death,
    chainRouters,
    survive10WavesNoBlocks,
    useFlameAmmo,
    coolTurret,
    enablePixelation,
    openWiki,
    ;

    private final SStat stat;
    private final int statGoal;
    public static final SAchievement[] ALL_ACHIEVEMENTS = values();

    /** Creates an achievement that is triggered when this stat reaches a number.*/
    SAchievement(SStat stat, int goal){
        this.stat = stat;
        this.statGoal = goal;
    }

    SAchievement(){
        this(null, 0);
    }

    public void complete(){
        if(!isAchieved()){
            SVarsUtil.stats.stats.setAchievement(name());
            SVarsUtil.stats.stats.storeStats();
        }
    }

    public void checkCompletion(){
        if(!isAchieved() && stat != null && stat.get() >= statGoal){
            complete();
        }
    }

    public boolean isAchieved(){
        return SVarsUtil.stats.stats.isAchieved(name(), false);
    }
}
