
package org.openmaptiles.addons;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmReader;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.util.MemoryEstimator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openmaptiles.Layer;
import org.openmaptiles.OpenMapTilesProfile;
import org.openmaptiles.generated.Tables;
import org.openmaptiles.util.Utils;

public class PublicTransport implements
  Layer,
  ForwardingProfile.OsmRelationPreprocessor,
  Tables.OsmHighwayLinestring.Handler,
  Tables.OsmRailwayLinestring.Handler,
  Tables.OsmShipwayLinestring.Handler,
  OpenMapTilesProfile.IgnoreWikidata,
  ForwardingProfile.LayerPostProcessor {


  private static final String LAYER_NAME = "public_transport";
  private static final String ROUTE_ID_TAG = "__route_id";

  @Override
  public String name() {
    return LAYER_NAME;
  }

  @Override
  public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
    if (relation.hasTag("route", "bus", "coach", "train", "tram", "subway", "ferry", "trolleybus", "monorail",
      "light_rail")) {
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
        String routeId =
          String.join("-", relation.route, relation.ref, relation.network, relation.operator, relation.name);
        features.line(LAYER_NAME)
          .setBufferPixels(30)
          .setMinZoom(0)
          .setMinPixelSize(0)
          .setAttr(ROUTE_ID_TAG, routeId)
          .setAttr("class", relation.route)
          .setAttr("subclass", relation.subclass)
          .setAttr("ref", relation.ref)
          .setAttr("network", relation.network)
          .setAttr("operator", relation.operator)
          .setAttr("colour", relation.colour)
          .setAttr("name", relation.name)
          .setAttr("brunnel",
            Utils.brunnel(feature.hasTag("bridge", "yes"), feature.hasTag("tunnel", "yes"),
              feature.hasTag("ford", "yes")))
          .setAttr("layer", feature.getLong("layer"));
      }
    }
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) {
    return FeatureMerge.mergeLineStrings(items,
      0, // after merging, remove lines that are still less than 0.5px long
      0.1, // simplify output linestrings using a 0.1px tolerance
      8 // remove any detail more than 4px outside the tile boundary
    );
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
