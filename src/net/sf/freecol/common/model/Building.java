/**
 *  Copyright (C) 2002-2012   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import net.sf.freecol.common.util.Utils;


/**
 * Represents a building in a colony.
 */
public class Building extends WorkLocation implements Named, Comparable<Building>, Consumer {

    @SuppressWarnings("unused")
    private static final Logger logger = Logger.getLogger(Building.class.getName());

    public static final String UNIT_CHANGE = "UNIT_CHANGE";

    /** The type of building. */
    protected BuildingType buildingType;


    /**
     * Constructor for ServerBuilding.
     */
    protected Building() {
        // empty constructor
    }

    /**
     * Constructor for ServerBuilding.
     *
     * @param game The <code>Game</code> this object belongs to.
     * @param colony The <code>Colony</code> in which this building is located.
     * @param type The <code>BuildingType</code> of building.
     */
    protected Building(Game game, Colony colony, BuildingType type) {
        super(game);
        setColony(colony);
        this.buildingType = type;
        // set production type to default value
        setDefaultProductionType(type);
    }

    /**
     * Initiates a new <code>Building</code> from an XML representation.
     *
     * @param game The <code>Game</code> this object belongs to.
     * @param in The input stream containing the XML.
     * @throws XMLStreamException if a problem was encountered during parsing.
     */
    public Building(Game game, XMLStreamReader in) throws XMLStreamException {
        super(game, in);

        readFromXML(in);
    }

    /**
     * Initiates a new <code>Building</code> with the given ID.  The
     * object should later be initialized by calling
     * {@link #readFromXML(XMLStreamReader)}.
     *
     * @param game The <code>Game</code> in which this object belongs.
     * @param id The unique identifier for this object.
     */
    public Building(Game game, String id) {
        super(game, id);
    }

    /**
     * Gets the type of this building.
     *
     * @return The building type.
     */
    public BuildingType getType() {
        return buildingType;
    }

    private void setDefaultProductionType(BuildingType type) {
        List<ProductionType> production = type.getProductionTypes();
        if (production == null || production.isEmpty()) {
            setProductionType(null);
        } else {
            setProductionType(production.get(0));
        }
    }

    /**
     * Changes the type of the Building.  The type of a building may
     * change when it is upgraded or damaged.
     *
     * @param newBuildingType The new <code>BuildingType</code>.
     * @see #upgrade
     * @see #damage
     */
    private void setType(final BuildingType newBuildingType) {
        // remove features from current type
        Colony colony = getColony();
        colony.removeFeatures(buildingType);

        if (newBuildingType != null) {
            buildingType = newBuildingType;

            // change default production type
            setDefaultProductionType(newBuildingType);

            // add new features and abilities from new type
            colony.addFeatures(buildingType);

            // Colonists which can't work here must be put outside
            for (Unit unit : getUnitList()) {
                if (!canAddType(unit.getType())) {
                    unit.setLocation(colony.getTile());
                }
            }
        }

        // Colonists exceding units limit must be put outside
        if (getUnitCount() > getUnitCapacity()) {
            for (Unit unit : getUnitList().subList(getUnitCapacity(),
                                                   getUnitCount())) {
                unit.setLocation(colony.getTile());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getNameKey() {
        return getType().getNameKey();
    }

    /**
     * Gets the level of this building.
     * Delegates to type.
     *
     * @return The building level.
     */
    public int getLevel() {
        return getType().getLevel();
    }

    /**
     * Gets the name of the improved building of the same type.
     * An improved building is a building of a higher level.
     *
     * @return The name of the improved building or <code>null</code> if the
     *     improvement does not exist.
     */
    public String getNextNameKey() {
        final BuildingType next = getType().getUpgradesTo();
        return (next == null) ? null : next.getNameKey();
    }

    /**
     * Does this building have a higher level?
     *
     * @return True if this <code>Building</code> can have a higher level.
     */
    public boolean canBuildNext() {
        return getColony().canBuild(getType().getUpgradesTo());
    }

    /**
     * Can this building can be damaged?
     *
     * @return True if this building can be damaged.
     * @see #damage
     */
    public boolean canBeDamaged() {
        return !getType().isAutomaticBuild()
            && !getColony().isAutomaticBuild(getType());
    }

    /**
     * Downgrade this building.
     *
     * @return True if the building was downgraded.
     */
    public boolean downgrade() {
        if (canBeDamaged()) {
            setType(getType().getUpgradesFrom());
            getColony().invalidateCache();
            return true;
        }
        return false;
    }

    /**
     * Upgrade this building to next level.
     *
     * @return True if the upgrade succeeds.
     */
    public boolean upgrade() {
        if (canBuildNext()) {
            setType(getType().getUpgradesTo());
            getColony().invalidateCache();
            return true;
        }
        return false;
    }

    /**
     * Gets the unit type that is the expert for this <code>Building</code>.
     *
     * @return The expert <code>UnitType</code>.
     */
    public UnitType getExpertUnitType() {
        return getSpecification().getExpertForProducing(getGoodsOutputType());
    }

    /**
     * Can a particular type of unit be added to this building?
     *
     * @param unitType The <code>UnitType</code> to check.
     * @return True if unit type can be added to this building.
     */
    public boolean canAddType(UnitType unitType) {
        return canBeWorked() && getType().canAdd(unitType);
    }

    AbstractGoods getOutput() {
        List<AbstractGoods> outputs = getOutputs();
        if (outputs == null || outputs.isEmpty()) {
            return null;
        } else {
            return outputs.get(0);
        }
    }

    /**
     * Gets the type of goods this <code>Building</code> produces.
     *
     * @return The type of goods this <code>Building</code> produces, or
     *     null if none.
     */
    public GoodsType getGoodsOutputType() {
        AbstractGoods output = getOutput();
        return output == null ? null : output.getType();
    }

    /**
     * Gets the type of goods this building needs for input.
     * in order to produce it's {@link #getGoodsOutputType output}.
     *
     * @return The type of goods this <code>Building</code> requires as input,
     *     or null if none.
     */
    public GoodsType getGoodsInputType() {
        List<AbstractGoods> inputs = getInputs();
        if (inputs == null || inputs.isEmpty()) {
            return null;
        } else {
            return inputs.get(0).getType();
        }
    }

    /**
     * Gets the maximum productivity of a unit working in this work
     * location, considering *only* the contribution of the unit,
     * exclusive of that of the work location.
     *
     * Used below, only public for the test suite.
     *
     * @param unit The <code>Unit</code> to check.
     * @return The maximum return from this unit.
     */
    public int getUnitProduction(Unit unit) {
        AbstractGoods output = getOutput();
        if (output == null || unit == null) {
            return 0;
        } else {
            int productivity = output.getAmount();
            if (productivity > 0) {
                final GoodsType goodsType = output.getType();
                final UnitType unitType = unit.getType();
                final Turn turn = getGame().getTurn();

                productivity = (int) FeatureContainer
                    .applyModifiers(productivity, turn,
                                    getProductionModifiers(goodsType, unitType));
            }
            return Math.max(0, productivity);
        }
    }

    /**
     * Gets the production information for this building taking account
     * of the available input and output goods.
     *
     * @param output The output goods already available in the colony,
     *     necessary in order to avoid excess production.
     * @param input The input goods available.
     * @return The production information.
     * @see ProductionCache#update
     */
    public ProductionInfo getAdjustedProductionInfo(AbstractGoods output,
                                                    List<AbstractGoods> input) {
        ProductionInfo result = new ProductionInfo();
        GoodsType outputType = getGoodsOutputType();
        GoodsType inputType = getGoodsInputType();
        int amountPresent = (output == null) ? 0 : output.getAmount();

        if (outputType != null && output != null
            && outputType != output.getType()) {
            throw new IllegalArgumentException("Wrong output type: "
                + output.getType() + " should have been: " + outputType);
        }
        int capacity = getColony().getWarehouseCapacity();
        if (getType().hasAbility(Ability.AVOID_EXCESS_PRODUCTION)
            && amountPresent >= capacity) {
            // warehouse is already full: produce nothing
            return result;
        }

        int availableInput = 0;
        if (inputType != null) {
            boolean found = false;
            for (AbstractGoods goods : input) {
                if (goods.getType() == inputType) {
                    availableInput = goods.getAmount();
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("No input goods of type "
                                                   + inputType + " available.");
            }
        }

        if (outputType != null) {
            int maximumInput = 0;
            if (inputType != null && canAutoProduce()) {
                int available = getColony().getGoodsCount(outputType);
                if (available >= outputType.getBreedingNumber()) {
                    // we need at least these many horses/animals to breed
                    int divisor = (int)getType().applyModifier(0f,
                        "model.modifier.breedingDivisor");
                    int factor = (int)getType().applyModifier(0f,
                        "model.modifier.breedingFactor");
                    maximumInput = ((available - 1) / divisor + 1) * factor;
                }
            } else {
                for (Unit u : getUnitList()) {
                    maximumInput += getUnitProduction(u);
                }
                maximumInput = Math.max(0, maximumInput);
            }
            Turn turn = getGame().getTurn();
            List<Modifier> productionModifiers = getProductionModifiers(getGoodsOutputType(), null);
            int maxProd = (int)FeatureContainer.applyModifiers(maximumInput,
                turn, productionModifiers);
            int actualInput = (inputType == null)
                ? maximumInput
                : Math.min(maximumInput, availableInput);
            // experts in factory level buildings may produce a
            // certain amount of goods even when no input is available
            if (availableInput < maximumInput
                && getType().hasAbility(Ability.EXPERTS_USE_CONNECTIONS)
                && getSpecification().getBoolean(GameOptions.EXPERTS_HAVE_CONNECTIONS)) {
                int minimumGoodsInput = 0;
                for (Unit unit: getUnitList()) {
                    if (unit.getType() == getExpertUnitType()) {
                        // TODO: put magic number in specification
                        minimumGoodsInput += 4;
                    }
                }
                if (minimumGoodsInput > availableInput) {
                    actualInput = minimumGoodsInput;
                }
            }
            // output is the same as input, plus production bonuses
            int prod = (int)FeatureContainer.applyModifiers(actualInput, turn,
                                                            productionModifiers);
            if (prod > 0) {
                if (getType().hasAbility(Ability.AVOID_EXCESS_PRODUCTION)) {
                    int total = amountPresent + prod;
                    while (total > capacity) {
                        if (actualInput <= 0) {
                            // produce nothing
                            return result;
                        } else {
                            actualInput--;
                        }
                        prod = (int)FeatureContainer.applyModifiers(actualInput,
                            turn, productionModifiers);
                        total = amountPresent + prod;
                        // in this case, maximum production does not
                        // exceed actual production
                        maximumInput = actualInput;
                        maxProd = prod;
                    }
                }
                prod = Math.max(0, prod);
                maxProd = Math.max(0, maxProd);
                result.addProduction(new AbstractGoods(outputType, prod));
                if (maxProd > prod) {
                    result.addMaximumProduction(new AbstractGoods(outputType, maxProd));
                }
                if (inputType != null) {
                    result.addConsumption(new AbstractGoods(inputType, actualInput));
                    if (maximumInput > actualInput) {
                        result.addMaximumConsumption(new AbstractGoods(inputType, maximumInput));
                    }
                }
            }
        }
        return result;
    }


    // Interface Comparable
    /**
     * {@inheritDoc}
     */
    public int compareTo(Building other) {
        return getType().compareTo(other.getType());
    }


    // Interface Location

    /**
     * {@inheritDoc}
     */
    @Override
    public StringTemplate getLocationName() {
        return StringTemplate.template("inLocation")
            .add("%location%", getNameKey());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean add(final Locatable locatable) {
        NoAddReason reason = getNoAddReason(locatable);
        if (reason != NoAddReason.NONE) {
            throw new IllegalStateException("Can not add " + locatable
                + " to " + toString() + " because " + reason);
        }
        Unit unit = (Unit) locatable;
        if (contains(unit)) return true;

        if (super.add(unit)) {
            unit.setState(Unit.UnitState.IN_COLONY);
            // TODO: remove this if we ever allow buildings to produce
            // more than one goods type.
            unit.setWorkType(getGoodsOutputType());

            getColony().invalidateCache();
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean remove(final Locatable locatable) {
        if (!(locatable instanceof Unit)) {
            throw new IllegalStateException("Not a unit: " + locatable);
        }
        Unit unit = (Unit) locatable;
        if (!contains(unit)) return true;

        if (super.remove(unit)) {
            unit.setState(Unit.UnitState.ACTIVE);
            unit.setMovesLeft(0);

            getColony().invalidateCache();
            return true;
        }
        return false;
    }


    // Interface UnitLocation

    /**
     * {@inheritDoc}
     */
    @Override
    public NoAddReason getNoAddReason(Locatable locatable) {
        if (!(locatable instanceof Unit)) return NoAddReason.WRONG_TYPE;
        NoAddReason reason = getNoWorkReason();
        Unit unit = (Unit) locatable;
        BuildingType type = getType();

        return (reason != NoAddReason.NONE) ? reason
            : !type.canAdd(unit.getType()) ? NoAddReason.MISSING_SKILL
            : super.getNoAddReason(locatable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getUnitCapacity() {
        return getType().getWorkPlaces();
    }


    // Interface WorkLocation

    /**
     * {@inheritDoc}
     */
    public NoAddReason getNoWorkReason() {
        return NoAddReason.NONE;
    }

    /**
     * {@inheritDoc}
     */
    public boolean canAutoProduce() {
        return getType().hasAbility(Ability.AUTO_PRODUCTION);
    }

    /**
     * {@inheritDoc}
     */
    public int getProductionOf(Unit unit, GoodsType goodsType) {
        if (unit == null) {
            throw new IllegalArgumentException("Null unit.");
        }
        int result = (getGoodsOutputType() == null
            || getGoodsOutputType() != goodsType) ? 0
            : getPotentialProduction(goodsType, unit.getType());
        return Math.max(0, result);
    }

    /**
     * {@inheritDoc}
     */
    public int getPotentialProduction(GoodsType goodsType, UnitType unitType) {
        AbstractGoods output = getOutput();
        if (output == null || output.getType() != goodsType) {
            return 0;
        } else {
            int production = (int) FeatureContainer
                .applyModifiers(output.getAmount(),
                                getGame().getTurn(),
                                getProductionModifiers(goodsType, unitType));
            return Math.max(0, production);
        }
    }

    /**
     * {@inheritDoc}
     */
    public List<Modifier> getProductionModifiers(GoodsType goodsType,
                                                 UnitType unitType) {
        List<Modifier> mods = new ArrayList<Modifier>();
        if (goodsType != null && goodsType == getGoodsOutputType()) {
            final BuildingType type = getType();
            final String id = goodsType.getId();
            final Turn turn = getGame().getTurn();
            final Player owner = getOwner();
            if (unitType != null) {
                // If a unit is present add unit specific bonuses and
                // unspecific owner bonuses (which includes things
                // like the Building national advantage).
                mods.addAll(getModifierSet(id, unitType, turn));
                mods.add(getColony().getProductionModifier(goodsType));
                //mods.add(type.getProductionModifier());
                mods.addAll(unitType.getModifierSet(id, goodsType, turn));
                if (owner != null) {
                    mods.addAll(owner.getModifierSet(id, unitType, turn));
                }
            } else {
                // If a unit is not present add only the bonuses
                // specific to the building (such as the Paine bells bonus).
                mods.addAll(getColony().getModifierSet(id, type, turn));
                if (owner != null) {
                    mods.addAll(owner.getModifierSet(id, type, turn));
                }
            }
        }
        return mods;
    }

    /**
     * {@inheritDoc}
     */
    public GoodsType getBestWorkType(Unit unit) {
        return getGoodsOutputType();
    }

    // Omitted getClaimTemplate, buildings do not need to be claimed.


    // Interface Consumer

    /**
     * Can this Consumer consume the given GoodsType?
     *
     * @param goodsType a <code>GoodsType</code> value
     * @return a <code>boolean</code> value
     */
    public boolean consumes(GoodsType goodsType) {
        return goodsType == getGoodsInputType();
    }

    /**
     * {@inheritDoc}
     */
    public List<AbstractGoods> getConsumedGoods() {
        List<AbstractGoods> result = new ArrayList<AbstractGoods>();
        GoodsType inputType = getGoodsInputType();
        if (inputType != null) {
            result.add(new AbstractGoods(inputType, 0));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public int getPriority() {
        return getType().getPriority();
    }

    /**
     * Is an ability present in this Building?
     *
     * The method actually returns whether the type of the building
     * has the required ability, since Buildings have no abilities
     * independent of their type.
     *
     * @param id The id of the ability to test.
     * @param type A <code>FreeColGameObjectType</code> (ignored).
     * @param turn A <code>Turn</code> (ignored).
     * @return True if the ability is present.
     */
    @Override
    public boolean hasAbility(String id, FreeColGameObjectType type,
                              Turn turn) {
        return getType().hasAbility(id);
    }

    /**
     * Gets the set of modifiers with the given Id from this Building.
     * Delegate to the type.
     *
     * @param id The id of the modifier to retrieve.
     * @param fcgot A <code>FreeColGameObjectType</code> (ignored).
     * @param turn A <code>Turn</code> (ignored).
     * @return A set of modifiers.
     */
    @Override
    public Set<Modifier> getModifierSet(String id, FreeColGameObjectType fcgot,
                                        Turn turn) {
        return getType().getModifierSet(id, fcgot, turn);
    }


    // Serialization

    private static final String BUILDING_TYPE_TAG = "buildingType";

    /**
     * {@inheritDoc}
     */
    @Override
    protected void toXMLImpl(XMLStreamWriter out, Player player,
                             boolean showAll, boolean toSavedGame) throws XMLStreamException {
        out.writeStartElement(getXMLElementTagName());

        writeAttributes(out);
        super.writeChildren(out, player, showAll, toSavedGame);

        out.writeEndElement();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(XMLStreamWriter out) throws XMLStreamException {
        super.writeAttributes(out);

        writeAttribute(out, BUILDING_TYPE_TAG, buildingType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readAttributes(XMLStreamReader in) throws XMLStreamException {
        super.readAttributes(in);

        final Specification spec = getSpecification();
        buildingType = spec.getType(in, BUILDING_TYPE_TAG,
                                    BuildingType.class, (BuildingType)null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void toXMLPartialImpl(XMLStreamWriter out, String[] fields) throws XMLStreamException {
        toXMLPartialByClass(out, Building.class, fields);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readFromXMLPartialImpl(XMLStreamReader in) throws XMLStreamException {
        readFromXMLPartialByClass(in, Building.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return Utils.lastPart(getType().getId(), ".")
            + "/" + getColony().getName();
    }

    /**
     * Gets the tag name of the root element representing this object.
     *
     * @return "building".
     */
    public static String getXMLElementTagName() {
        return "building";
    }
}
