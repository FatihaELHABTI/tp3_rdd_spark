package ma.enset.exercise1;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

public class VentesApp {
    public static void main(String[] args) {
        //SparkConf conf = new SparkConf().setAppName("Ventes Analysis").setMaster("local[*]");
        SparkConf conf = new SparkConf().setAppName("Ventes Cluster");
        JavaSparkContext sc = new JavaSparkContext(conf);

        //JavaRDD<String> lines = sc.textFile("ventes.txt");
        JavaRDD<String> lines = sc.textFile("/opt/ventes.txt");

        // 1. Total des ventes par ville
        JavaPairRDD<String, Double> ventesParVille = lines.mapToPair(line -> {
            String[] cols = line.split(" ");
            return new Tuple2<>(cols[1], Double.parseDouble(cols[3]));
        }).reduceByKey(Double::sum);

        System.out.println("Ventes par ville :");
        ventesParVille.collect().forEach(System.out::println);

        // 2. Total des ventes par ville et par année
        JavaPairRDD<String, Double> ventesVilleAnnee = lines.mapToPair(line -> {
            String[] cols = line.split(" ");
            String annee = cols[0].split("-")[0]; // Supposant format YYYY-MM-DD
            return new Tuple2<>(cols[1] + " " + annee, Double.parseDouble(cols[3]));
        }).reduceByKey(Double::sum);

        System.out.println("Ventes par ville et année :");
        ventesVilleAnnee.collect().forEach(System.out::println);

        sc.close();
    }
}