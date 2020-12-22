package wtf.listenia.sockets.converter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class SerializeMap {

    public static String map2str (Map<String, String> map) {
        return map.keySet().stream()
                .map(key -> (key + "=" + map.get(key)).trim())
                .collect(Collectors.joining(","));
    }

    public static Map<String, String> str2map (String mapAsString) {
        return Arrays.stream(mapAsString.split(","))
                .map(entry -> entry.split("="))
                .collect(Collectors.toMap(entry -> entry[0], entry -> entry[1]));
    }


}
