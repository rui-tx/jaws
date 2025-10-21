package org.ruitx.jaws.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.util.Map;
import org.ruitx.jaws.components.Njord;
import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseType;

public final class JawsOpenApiExporter {

  private final Njord njord;
  private final ObjectMapper mapper = Odin.getMapper();

  public JawsOpenApiExporter(Njord njord) {
    this.njord = njord;
  }

  private static String mediaTypeOf(ResponseType rt) {
    return rt == ResponseType.JSON ? "application/json" : "text/html";
  }

  // If your endpoints use colon or different placeholders, normalize to {param}
  private static String normalizePathTemplate(String path) {
    // Keep as-is if you already use /users/{id}. If you use :id style, convert here.
    return path;
  }

  public ObjectNode build(String title, String description, String version, String serverUrl) {
    ObjectNode root = mapper.createObjectNode();
    root.put("openapi", "3.0.0");

    // info
    ObjectNode info = mapper.createObjectNode();
    info.put("title", title);
    info.put("description", description);
    info.put("version", version);
    root.set("info", info);

    // servers
    ArrayNode servers = mapper.createArrayNode();
    ObjectNode server = mapper.createObjectNode();
    server.put("url", serverUrl);
    servers.add(server);
    root.set("servers", servers);

    // paths
    ObjectNode paths = mapper.createObjectNode();

    Map<String, Map<RequestType, Method>> table = njord.getRouteTable();
    for (Map.Entry<String, Map<RequestType, Method>> e : table.entrySet()) {
      String path = normalizePathTemplate(e.getKey());
      ObjectNode pathItem = (ObjectNode) paths.get(path);
      if (pathItem == null) {
        pathItem = mapper.createObjectNode();
        paths.set(path, pathItem);
      }

      for (Map.Entry<RequestType, Method> me : e.getValue().entrySet()) {
        String httpMethod = me.getKey().name().toLowerCase();
        Method handler = me.getValue();
        Route routeAnn = handler.getAnnotation(Route.class);

        ObjectNode op = mapper.createObjectNode();
        op.put("operationId",
            handler.getDeclaringClass().getSimpleName() + "_" + handler.getName());

        // Optional: If htmx=true => require HX-Request header
        if (routeAnn.htmx()) {
          ArrayNode params = (ArrayNode) op.get("parameters");
          if (params == null) {
            params = mapper.createArrayNode();
            op.set("parameters", params);
          }

          ObjectNode headerParam = mapper.createObjectNode();
          headerParam.put("name", "HX-Request");
          headerParam.put("in", "header");
          headerParam.put("required", true);
          ObjectNode schema = mapper.createObjectNode();
          schema.put("type", "string");
          headerParam.set("schema", schema);
          headerParam.put("description", "HTMX request header must be present");
          params.add(headerParam);
        }

        // Minimal responses
        ObjectNode responses = mapper.createObjectNode();
        ObjectNode r200 = mapper.createObjectNode();
        r200.put("description", "OK");

        String mediaType = mediaTypeOf(routeAnn.responseType());
        ObjectNode content = mapper.createObjectNode();
        ObjectNode media = mapper.createObjectNode();

        // If JSON and return type is not void, you could attach a schema later
        // media.set("schema", schemaForType(handler.getGenericReturnType()));

        content.set(mediaType, media);
        r200.set("content", content);
        responses.set("200", r200);
        op.set("responses", responses);

        pathItem.set(httpMethod, op);
      }
    }

    root.set("paths", paths);
    return root;
  }
}
