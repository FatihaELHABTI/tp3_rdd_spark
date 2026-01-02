package ma.enset.exercise2;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogAnalyzerApp {
    // Regex pour format Apache : IP - user [date] "method URL proto" code size
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\S+) \\S+ \\S+ \\[(.*?)\\] \"(\\S+) (\\S+)\\s*.*?\" (\\d{3}) (\\d+|-)"
    );

    public static void main(String[] args) {
        //SparkConf conf = new SparkConf().setAppName("Log Analyzer").setMaster("local[*]");
        SparkConf conf = new SparkConf().setAppName("Log Analyzer Cluster");
        JavaSparkContext sc = new JavaSparkContext(conf);

        //JavaRDD<String> logLines = sc.textFile("access.log");
        JavaRDD<String> logLines = sc.textFile("/opt/access.log");

        // Extraction des champs via un objet (ou map)
        JavaRDD<String[]> parsedLogs = logLines.map(line -> {
            Matcher m = LOG_PATTERN.matcher(line);
            if (m.find()) {
                return new String[]{ m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), m.group(6) };
            }
            return null;
        }).filter(x -> x != null).cache();

        // 3. Statistiques de base
        long totalReq = parsedLogs.count();
        long totalErrors = parsedLogs.filter(cols -> Integer.parseInt(cols[4]) >= 400).count();
        double errorRate = (double) totalErrors / totalReq * 100;

        System.out.println("Total requêtes: " + totalReq);
        System.out.println("Total erreurs: " + totalErrors);
        System.out.println("Taux d'erreur: " + errorRate + "%");

        // 4. Top 5 des adresses IP
        System.out.println("Top 5 IP:");
        parsedLogs.mapToPair(cols -> new Tuple2<>(cols[0], 1))
                .reduceByKey(Integer::sum)
                .mapToPair(Tuple2::swap)
                .sortByKey(false)
                .take(5).forEach(System.out::println);

        // 5. Top 5 des ressources
        System.out.println("Top 5 Ressources:");
        parsedLogs.mapToPair(cols -> new Tuple2<>(cols[3], 1))
                .reduceByKey(Integer::sum)
                .mapToPair(Tuple2::swap)
                .sortByKey(false)
                .take(5).forEach(System.out::println);

        // 6. Répartition par code HTTP
        System.out.println("Répartition par Code HTTP:");
        parsedLogs.mapToPair(cols -> new Tuple2<>(cols[4], 1))
                .reduceByKey(Integer::sum)
                .collect().forEach(System.out::println);

        sc.close();
    }
}