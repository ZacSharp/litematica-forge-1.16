package fi.dy.masa.litematica.materials;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fi.dy.masa.litematica.util.BlockInfoListType;
import fi.dy.masa.malilib.util.JsonUtils;
import net.minecraft.util.math.MathHelper;

public abstract class MaterialListBase
{
    protected final MaterialListHudRenderer hudRenderer = new MaterialListHudRenderer(this);
    protected final Set<MaterialListEntry> ignored = new HashSet<>();
    protected final List<MaterialListEntry> materialListPreFiltered = new ArrayList<>();
    protected final List<MaterialListEntry> materialListFiltered = new ArrayList<>();
    protected ImmutableList<MaterialListEntry> materialListAll = ImmutableList.of();
    protected SortCriteria sortCriteria = SortCriteria.COUNT_TOTAL;
    protected BlockInfoListType materialListType = BlockInfoListType.ALL;
    protected boolean reverse;
    protected boolean hideAvailable;
    protected int multiplier = 1;

    protected abstract List<MaterialListEntry> createMaterialListEntries();

    public abstract String getName();

    public abstract String getTitle();

    public MaterialListHudRenderer getHudRenderer()
    {
        return this.hudRenderer;
    }

    public ImmutableList<MaterialListEntry> getMaterialsAll()
    {
        return this.materialListAll;
    }

    public List<MaterialListEntry> getMaterialsFiltered(boolean refresh)
    {
        if (this.hideAvailable)
        {
            return this.getMaterialsMissingOnly(refresh);
        }

        return this.materialListPreFiltered;
    }

    public List<MaterialListEntry> getMaterialsMissingOnly(boolean refresh)
    {
        if (refresh)
        {
            this.recreateFilteredList();
        }

        return this.materialListFiltered;
    }

    public void recreateFilteredList()
    {
        this.materialListFiltered.clear();

        for (int i = 0; i < this.materialListPreFiltered.size(); ++i)
        {
            MaterialListEntry entry = this.materialListPreFiltered.get(i);
            int countMissing = this.multiplier == 1 ? entry.getCountMissing() : this.multiplier * entry.getCountTotal();

            if (entry.getCountAvailable() < countMissing)
            {
                this.materialListFiltered.add(entry);
            }
            // Remove entries that have been seen as available at least at one point
            // (for example when gathering resources to a staging area)
            else if (this.hideAvailable)
            {
                this.materialListPreFiltered.remove(i);
                --i;
            }
        }
    }

    public void ignoreEntry(MaterialListEntry entry)
    {
        this.ignored.add(entry);
        this.materialListPreFiltered.remove(entry);
        this.recreateFilteredList();
    }

    public void clearIgnored()
    {
        this.ignored.clear();
        this.refreshPreFilteredList();
        this.recreateFilteredList();
    }

    /**
     * Re-creates the all materials list from the schematic or placement
     */
    public void recreateMaterialList()
    {
        this.materialListAll = ImmutableList.copyOf(this.createMaterialListEntries());
        this.refreshPreFilteredList();
    }

    /**
     * Resets the pre-filtered materials list to the all materials list
     */
    public void refreshPreFilteredList()
    {
        this.materialListPreFiltered.clear();
        this.materialListPreFiltered.addAll(this.materialListAll);
        this.materialListPreFiltered.removeAll(this.ignored);
    }

    public SortCriteria getSortCriteria()
    {
        return this.sortCriteria;
    }

    public boolean getSortInReverse()
    {
        return this.reverse;
    }

    public boolean getHideAvailable()
    {
        return this.hideAvailable;
    }

    public int getMultiplier()
    {
        return this.multiplier;
    }

    public void setSortCriteria(SortCriteria criteria)
    {
        if (this.sortCriteria == criteria)
        {
            this.reverse = ! this.reverse;
        }
        else
        {
            this.sortCriteria = criteria;
            this.reverse = false;
        }
    }

    public void setHideAvailable(boolean hideAvailable)
    {
        this.hideAvailable = hideAvailable;
    }

    public void setMultiplier(int multiplier)
    {
        this.multiplier = MathHelper.clamp(multiplier, 1, Integer.MAX_VALUE);
    }

    public BlockInfoListType getMaterialListType()
    {
        return this.materialListType;
    }

    public void setMaterialListType(BlockInfoListType type)
    {
        this.materialListType = type;
    }

    public JsonObject toJson()
    {
        JsonObject obj = new JsonObject();

        obj.add("type", new JsonPrimitive(this.getMaterialListType().getStringValue()));
        obj.add("sort_criteria", new JsonPrimitive(this.sortCriteria.name()));
        obj.add("sort_reverse", new JsonPrimitive(this.reverse));
        obj.add("hide_available", new JsonPrimitive(this.hideAvailable));
        obj.add("multiplier", new JsonPrimitive(this.multiplier));

        return obj;
    }

    public void fromJson(JsonObject obj)
    {
        if (JsonUtils.hasString(obj, "type"))
        {
            this.setMaterialListType(BlockInfoListType.fromStringStatic(JsonUtils.getString(obj, "type")));
        }

        if (JsonUtils.hasString(obj, "sort_criteria"))
        {
            this.sortCriteria = SortCriteria.fromStringStatic(JsonUtils.getString(obj, "sort_criteria"));
        }

        this.reverse = JsonUtils.getBooleanOrDefault(obj, "sort_reverse", false);
        this.hideAvailable = JsonUtils.getBooleanOrDefault(obj, "hide_available", false);
        this.multiplier = JsonUtils.getIntegerOrDefault(obj, "multiplier", 1);
    }

    public enum SortCriteria
    {
        NAME,
        COUNT_TOTAL,
        COUNT_MISSING,
        COUNT_AVAILABLE;

        public static SortCriteria fromStringStatic(String name)
        {
            for (SortCriteria mode : SortCriteria.values())
            {
                if (mode.name().equalsIgnoreCase(name))
                {
                    return mode;
                }
            }

            return SortCriteria.COUNT_TOTAL;
        }
    }
}
