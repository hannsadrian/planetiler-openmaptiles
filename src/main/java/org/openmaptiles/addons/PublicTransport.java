
package org.openmaptiles.addons;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmReader;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.util.MemoryEstimator;
import org.openmaptiles.Layer;
import org.openmaptiles.OpenMapTilesProfile;
import org.openmaptiles.generated.Tables;

import java.util.List;

public class PublicTransport implements
  Layer,
  ForwardingProfile.OsmRelationPreprocessor,
  Tables.OsmHighwayLinestring.Handler,
  Tables.OsmRailwayLinestring.Handler,
  Tables.OsmShipwayLinestring.Handler,
  OpenMapTilesProfile.IgnoreWikidata {


    private static final String LAYER_NAME = "public_transport";

    @Override
    public String name() {
        return LAYER_NAME;
    }

    @Override
    public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
        if (relation.hasTag("route", "bus", "coach", "train", "tram", "subway", "ferry", "trolleybus", "monorail", "light_rail")) {
            String route = relation.getString("route");
            String subclass = relation.getString("service");
            if ("coach".equals(route)) {
                subclass = "coach";
            }
            String ref = relation.getString("ref");
            String network = relation.getString("network");
            String operator = relation.getString("operator");
            String colour = relation.getString("colour");
            String name = relation.getString("name");
            return List.of(new RouteRelation(route, subclass, ref, network, operator, colour, name, relation.id()));
        }
        return null;
    }

    @Override
    public void process(Tables.OsmHighwayLinestring element, FeatureCollector features) {
        processWay(element.source(), features);
    }

    @Override
    public void process(Tables.OsmRailwayLinestring element, FeatureCollector features) {
        processWay(element.source(), features);
    }

    @Override
    public void process(Tables.OsmShipwayLinestring element, FeatureCollector features) {
        processWay(element.source(), features);
    }

    private void processWay(SourceFeature feature, FeatureCollector features) {
        List<OsmReader.RelationMember<RouteRelation>> relations = feature.relationInfo(RouteRelation.class);
        if (!relations.isEmpty()) {
            for (var member : relations) {
                RouteRelation relation = member.relation();
                features.line(LAYER_NAME)
                    .setBufferPixels(4)
                    .setMinZoom(0)
                    .setAttr("class", relation.route)
                    .setAttr("subclass", relation.subclass)
                    .setAttr("ref", relation.ref)
                    .setAttr("network", relation.network)
                    .setAttr("operator", relation.operator)
                    .setAttr("colour", relation.colour)
                    .setAttr("name", relation.name);
            }
        }
    }

    record RouteRelation(
        String route,
        String subclass,
        String ref,
        String network,
        String operator,
        String colour,
        String name,
        @Override long id
    ) implements OsmRelationInfo {

        @Override
        public long estimateMemoryUsageBytes() {
            return MemoryEstimator.estimateSize(route) +
                MemoryEstimator.estimateSize(subclass) +
                MemoryEstimator.estimateSize(ref) +
                MemoryEstimator.estimateSize(network) +
                MemoryEstimator.estimateSize(operator) +
                MemoryEstimator.estimateSize(colour) +
                MemoryEstimator.estimateSize(name) +
                Long.BYTES;
        }
    }
}
