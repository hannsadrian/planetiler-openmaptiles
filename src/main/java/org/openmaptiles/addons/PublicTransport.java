package org.openmaptiles.addons;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.util.MemoryEstimator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PublicTransport implements
  Layer,
  ForwardingProfile.OsmRelationPreprocessor,
  ForwardingProfile.FeatureProcessor,
  ForwardingProfile.LayerPostProcessor {

  private static final String LAYER_NAME = "public_transport";
  private static final String ROUTE_ID_TAG = "__route_id";

  @Override
  public String name() {
    return LAYER_NAME;
  }

  // SCHRITT 1: Relationen vorverarbeiten, um jeden Way mit Routen-Infos zu "stempeln"
  // Das war in Ihrem ersten Code schon goldrichtig.
  @Override
  public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
    if (relation.hasTag("route", "bus", "coach", "train", "tram", "subway", "ferry", "trolleybus", "monorail", "light_rail")) {
      // Wir erstellen einen stabilen, eindeutigen Identifier für die Route
      String routeId = String.join("|",
        relation.getString("route"),
        relation.getString("ref"),
        relation.getString("network"),
        relation.getString("operator"),
        relation.getString("name")
      );
      return List.of(new RouteInfo(routeId, relation.id()));
    }
    return null;
  }

  // SCHRITT 2: Features für jeden einzelnen Way erstellen, inklusive der brunnel/layer-Tags
  @Override
  public void processFeature(SourceFeature feature, FeatureCollector features) {
    // Wir interessieren uns nur für Linien (ways), die Teil unserer Routen-Relationen sind
    if (feature.isLine()) {
      List<RouteInfo> relations = feature.relationInfo(RouteInfo.class);
      if (!relations.isEmpty()) {
        for (var relationInfo : relations) {
          OsmElement.Relation relation = feature.source().getRelation(relationInfo.relationId());
          String routeType = relation.getString("route");

          int minZoom = switch (routeType) {
            case "ferry" -> 7;
            case "train", "subway", "light_rail", "monorail" -> 8;
            case "tram" -> 11;
            default -> 12;
          };

          features.line(LAYER_NAME)
            .setBufferPixels(4) // Wichtig für saubere Übergänge
            .setMinPixelSize(0) // Wir verwerfen erstmal nichts, das macht postProcess
            .setZoomRange(minZoom, 14)
            .setAttr(ROUTE_ID_TAG, relationInfo.routeId()) // Unser Merge-Schlüssel
            // Die kritischen Attribute, die das Merging bisher verhindert haben:
            .setAttr("brunnel", feature.getTag("brunnel"))
            .setAttr("layer", feature.getTag("layer"))
            // Die restlichen, für alle Segmente gleichen Attribute:
            .setAttr("class", routeType)
            .setAttr("ref", relation.getString("ref"))
            .setAttr("network", relation.getString("network"))
            .setAttr("operator", relation.getString("operator"))
            .setAttr("colour", relation.getString("colour"))
            .setAttr("name", relation.getString("name"));
        }
      }
    }
  }

  // SCHRITT 3: Die Magie - Ein smarter Post-Processing Schritt
  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) {
    // Gruppiere alle Segmente nach ihrer eindeutigen Routen-ID
    Map<Object, List<VectorTile.Feature>> groupedByRoute = items.stream()
      .collect(Collectors.groupingBy(f -> f.tags().get(ROUTE_ID_TAG)));

    List<VectorTile.Feature> result = new ArrayList<>();

    for (List<VectorTile.Feature> group : groupedByRoute.values()) {
      // Für jede Gruppe (d.h. für jede einzelne Bus-/Bahnlinie)...
      // ... entferne TEMPORÄR die Attribute, die das Zusammenfügen verhindern.
      List<VectorTile.Feature> groupWithoutConflictingAttrs = new ArrayList<>();
      for (VectorTile.Feature feature : group) {
        // Erstelle eine Kopie der Tags OHNE brunnel und layer
        var newTags = feature.tags().entrySet().stream()
          .filter(e -> !e.getKey().equals("brunnel") && !e.getKey().equals("layer"))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Erstelle ein neues Feature mit der gleichen Geometrie aber den "sauberen" Tags
        groupWithoutConflictingAttrs.add(new VectorTile.Feature(
          feature.layer(), feature.id(), feature.geometry(), newTags
        ));
      }

      // Führe jetzt den Merge auf den "sauberen" Features durch.
      // Da brunnel/layer weg sind, werden alle Teile einer Route zu einer Linie zusammengefügt.
      List<VectorTile.Feature> merged = FeatureMerge.mergeLineStrings(groupWithoutConflictingAttrs,
        0.5, // Mindestlänge in Pixeln
        0.5, // Vereinfachungstoleranz
        4,   // Puffer
        ROUTE_ID_TAG
      );

      // ANMERKUNG: Durch diesen Prozess verlieren die zusammengefügten Linien die spezifischen
      // brunnel/layer Tags. Die Linie ist jetzt durchgehend, aber man kann die Brücke nicht
      // mehr separat stylen. Das ist der klassische Kompromiss: Kontinuität vs. Detail-Attribute.
      // Für die meisten Anwendungsfälle ist eine durchgehende Linie wichtiger.
      result.addAll(merged);
    }

    return result;
  }

  // Ein einfacher Record, um die Routen-ID zu speichern
  record RouteInfo(String routeId, long relationId) implements OsmRelationInfo {
    @Override
    public long estimateMemoryUsageBytes() {
      return MemoryEstimator.estimateSize(routeId) + Long.BYTES;
    }
  }
}
