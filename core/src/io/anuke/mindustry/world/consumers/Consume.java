package io.anuke.mindustry.world.consumers;

import io.anuke.arc.scene.ui.layout.Table;
import io.anuke.mindustry.entities.type.TileEntity;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.meta.BlockStats;

/** An abstract class that defines a type of resource that a block can consume. */
public abstract class Consume{
    /** If true, this consumer will not influence consumer validity. */
    protected boolean optional;
    /** If true, this consumer will be displayed as a boost input. */
    protected boolean booster;
    protected boolean update = true;

    /**
     * Apply a filter to items accepted.
     * This should set all item IDs that are present in the filter to true.
     * This really shouldnt be empty and should be implemented in a subclass, However this is an
     * architecture design that would require more refactor to remove the issue
     */
    public void applyItemFilter(boolean[] filter){

    }

    /**
     * Apply a filter to liquids accepted.
     * This should set all liquid IDs that are present in the filter to true.
     * This really shouldnt be empty and should be implemented in a subclass, However this is an
     * architecture design that would require more refactor to remove the issue
     */
    public void applyLiquidFilter(boolean[] filter){

    }

    public Consume optional(boolean optional, boolean boost){
        this.optional = optional;
        this.booster = boost;
        return this;
    }

    public Consume boost(){
        return optional(true, true);
    }

    public Consume update(boolean update){
        this.update = update;
        return this;
    }

    public boolean isOptional(){
        return optional;
    }

    public boolean isBoost(){
        return booster;
    }

    public boolean isUpdate(){
        return update;
    }

    public abstract ConsumeType type();

    public abstract void build(Tile tile, Table table);

    /** Called when a consumption is triggered manually.
     * This really shouldnt be empty and should be implemented in a subclass, However this is an
     * architecture design that would require more refactor to remove the issue
     * */
    public void trigger(TileEntity entity){

    }

    public abstract String getIcon();

    public abstract void update(TileEntity entity);

    public abstract boolean valid(TileEntity entity);

    public abstract void display(BlockStats stats);
}
