# TP3 - Traitement Distribué avec Apache Spark et RDD

## Table des matières
- [Introduction](#introduction)
- [Architecture du Projet](#architecture-du-projet)
- [Technologies Utilisées](#technologies-utilisées)
- [Structure du Projet](#structure-du-projet)
- [Configuration de l'Environnement](#configuration-de-lenvironnement)
- [Exercice 1 : Analyse des Ventes](#exercice-1--analyse-des-ventes)
- [Exercice 2 : Analyse des Logs Apache](#exercice-2--analyse-des-logs-apache)
- [Compilation et Packaging](#compilation-et-packaging)
- [Déploiement sur le Cluster](#déploiement-sur-le-cluster)
- [Exécution et Résultats](#exécution-et-résultats)
- [Commandes Utiles](#commandes-utiles)

---

## Introduction

Ce projet implémente deux applications de traitement distribué de données utilisant Apache Spark avec l'API RDD (Resilient Distributed Dataset). Les applications sont déployées sur un cluster Hadoop/Spark conteneurisé avec Docker, permettant de traiter des données volumineuses de manière distribuée et parallèle.

Le premier exercice analyse des données de ventes pour calculer des agrégations par ville et par année. Le second exercice traite des logs de serveur Apache pour extraire des statistiques sur le trafic, les erreurs et l'utilisation des ressources.

---

## Architecture du Projet

Le projet utilise une architecture distribuée composée de plusieurs conteneurs Docker orchestrés via Docker Compose :

**Couche Hadoop HDFS :**
- **NameNode** : Gère les métadonnées du système de fichiers distribué (port 9870 pour l'interface web, port 8020 pour RPC)
- **DataNode** : Stocke les blocs de données répliqués

**Couche YARN (Resource Manager) :**
- **ResourceManager** : Orchestre l'allocation des ressources du cluster (port 8088)
- **NodeManager** : Gère les ressources sur chaque nœud worker

**Couche Spark :**
- **Spark Master** : Coordonne l'exécution des applications Spark (port 7077 pour les workers, port 8080 pour l'interface web)
- **Spark Worker** : Exécute les tâches distribuées de calcul

Cette architecture permet un traitement parallèle et tolérant aux pannes grâce à la réplication des données sur HDFS et la distribution des calculs via Spark.

---

## Technologies Utilisées

### Apache Spark 3.5.0
Framework de calcul distribué open-source permettant le traitement rapide de grandes quantités de données. Spark offre une API de haut niveau pour manipuler des collections distribuées (RDD) et supporte plusieurs langages dont Java, Scala et Python.

**Caractéristiques principales :**
- Traitement en mémoire pour des performances élevées
- API RDD pour les transformations et actions distribuées
- Support natif pour les opérations de type MapReduce
- Tolérance aux pannes via la lignée des RDD

### Hadoop 3.3.6
Plateforme de stockage distribué et de traitement de données massives.

**Composants utilisés :**
- **HDFS** : Système de fichiers distribué permettant le stockage redondant des données
- **YARN** : Gestionnaire de ressources pour l'ordonnancement des applications

### Maven
Outil de gestion de projet et de build automation qui gère les dépendances et la compilation du code Java.

**Configuration principale :**
- Compilation Java 17
- Maven Shade Plugin pour créer un JAR exécutable avec toutes les dépendances

### Docker et Docker Compose
Plateforme de conteneurisation permettant d'isoler et de déployer facilement l'infrastructure complète du cluster.

---

## Structure du Projet

```
tp3_rdd/
├── src/
│   └── main/
│       └── java/
│           └── ma/
│               └── enset/
│                   ├── exercise1/
│                   │   └── VentesApp.java
│                   └── exercise2/
│                       └── LogAnalyzerApp.java
├── target/
│   ├── classes/
│   ├── generated-sources/
│   ├── maven-archiver/
│   ├── maven-status/
│   ├── test-classes/
│   ├── original-tp3_rdd-1.0-SNAPSHOT.jar
│   └── tp3_rdd-1.0-SNAPSHOT.jar
├── volumes/
├── access.log
├── ventes.txt
├── config
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## Configuration de l'Environnement

### Fichier de Configuration Hadoop (`config`)

Ce fichier centralise toutes les configurations nécessaires pour Hadoop HDFS et YARN :

```properties
# Configuration HDFS
CORE-SITE.XML_fs.default.name=hdfs://namenode
CORE-SITE.XML_fs.defaultFS=hdfs://namenode
HDFS-SITE.XML_dfs.namenode.rpc-address=namenode:8020
HDFS-SITE.XML_dfs.replication=3

# Configuration MapReduce
MAPRED-SITE.XML_mapreduce.framework.name=yarn
MAPRED-SITE.XML_yarn.app.mapreduce.am.env=HADOOP_MAPRED_HOME=$HADOOP_HOME
MAPRED-SITE.XML_mapreduce.map.env=HADOOP_MAPRED_HOME=$HADOOP_HOME
MAPRED-SITE.XML_mapreduce.reduce.env=HADOOP_MAPRED_HOME=$HADOOP_HOME

# Configuration YARN
YARN-SITE.XML_yarn.resourcemanager.hostname=resourcemanager
YARN-SITE.XML_yarn.nodemanager.pmem-check-enabled=false
YARN-SITE.XML_yarn.nodemanager.delete.debug-delay-sec=600
YARN-SITE.XML_yarn.nodemanager.vmem-check-enabled=false
YARN-SITE.XML_yarn.nodemanager.aux-services=mapreduce_shuffle

# Configuration Capacity Scheduler
CAPACITY-SCHEDULER.XML_yarn.scheduler.capacity.maximum-applications=10000
CAPACITY-SCHEDULER.XML_yarn.scheduler.capacity.maximum-am-resource-percent=0.1
CAPACITY-SCHEDULER.XML_yarn.scheduler.capacity.root.queues=default
CAPACITY-SCHEDULER.XML_yarn.scheduler.capacity.root.default.capacity=100
```

**Points clés :**
- Le facteur de réplication HDFS est fixé à 3 pour assurer la redondance
- Les vérifications mémoire du NodeManager sont désactivées pour faciliter le développement
- Le service auxiliaire `mapreduce_shuffle` est activé pour supporter les opérations de tri distribuées

### Docker Compose (`docker-compose.yml`)

Le fichier Docker Compose orchestre l'ensemble des services :

```yaml
services:
  namenode:
    image: apache/hadoop:3.3.6
    hostname: namenode
    command: ["hdfs", "namenode"]
    ports:
      - "9870:9870"
      - "8020:8020"
    env_file:
      - ./config
    environment:
        ENSURE_NAMENODE_DIR: "/tmp/hadoop-root/dfs/name"
    volumes:
      - ./volumes/namenode:/data
    networks:
      - spark-network

  datanode:
    image: apache/hadoop:3.3.6
    command: ["hdfs", "datanode"]
    env_file:
      - ./config
    networks:
      - spark-network

  resourcemanager:
    image: apache/hadoop:3.3.6
    hostname: resourcemanager
    command: ["yarn", "resourcemanager"]
    ports:
      - "8088:8088"
    env_file:
      - ./config
    volumes:
      - ./test.sh:/opt/test.sh
    networks:
      - spark-network

  nodemanager:
    image: apache/hadoop:3.3.6
    command: ["yarn", "nodemanager"]
    env_file:
      - ./config
    networks:
      - spark-network

  spark-master:
    image: spark:latest
    hostname: spark-master
    container_name: spark-master
    command: ["/opt/spark/bin/spark-class", 
              "org.apache.spark.deploy.master.Master",
              "--host", "spark-master", 
              "--port", "7077", 
              "--webui-port", "8080"]
    ports: 
      - "7077:7077"
      - "8080:8080"
    depends_on: 
      - namenode
    networks:
      - spark-network

  spark-worker-1:
    image: spark:latest
    hostname: spark-worker-1
    container_name: spark-worker-1
    command: ["/opt/spark/bin/spark-class", 
              "org.apache.spark.deploy.worker.Worker",
              "spark://spark-master:7077"]
    depends_on: 
      - spark-master
    networks:
      - spark-network

networks:
  spark-network:
    driver: bridge
```

### Configuration Maven (`pom.xml`)

Le fichier POM définit les dépendances et les plugins de build :

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>ma.enset</groupId>
    <artifactId>tp3_rdd</artifactId>
    <version>1.0-SNAPSHOT</version>
    
    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    
    <dependencies>
        <!-- Spark Core -->
        <dependency>
            <groupId>org.apache.spark</groupId>
            <artifactId>spark-core_2.12</artifactId>
            <version>3.5.0</version>
        </dependency>
        
        <!-- SLF4J Simple Logger -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>2.0.7</version>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <!-- Maven Compiler Plugin -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>${maven.compiler.source}</source>
                    <target>${maven.compiler.target}</target>
                </configuration>
            </plugin>
            
            <!-- Maven Shade Plugin pour Fat JAR -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <filters>
                                <filter>
                                    <artifact>*:*</artifact>
                                    <excludes>
                                        <exclude>META-INF/*.SF</exclude>
                                        <exclude>META-INF/*.DSA</exclude>
                                        <exclude>META-INF/*.RSA</exclude>
                                    </excludes>
                                </filter>
                            </filters>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

**Le Maven Shade Plugin** crée un JAR avec toutes les dépendances intégrées, éliminant les problèmes de classpath lors du déploiement sur le cluster.

---

## Exercice 1 : Analyse des Ventes

### Objectif
Analyser un fichier de ventes pour calculer le total des ventes par ville et par ville-année en utilisant les opérations de transformation et de réduction sur les RDD.

### Format des Données (`ventes.txt`)

```
2023-10-10 Casablanca Laptop 1200
2023-10-11 Paris Smartphone 800
2023-11-15 Casablanca Tablet 450
2024-01-20 Lyon Laptop 1300
2024-02-10 Paris Laptop 1250
2024-03-05 Casablanca Smartphone 820
2024-05-12 Lyon Camera 600
```

Chaque ligne contient quatre champs séparés par des espaces : date, ville, produit et montant.

### Code Source (`VentesApp.java`)

```java
package ma.enset.exercise1;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

public class VentesApp {
    public static void main(String[] args) {
        // Configuration Spark pour le mode cluster
        SparkConf conf = new SparkConf().setAppName("Ventes Cluster");
        JavaSparkContext sc = new JavaSparkContext(conf);
        
        // Lecture du fichier depuis le système de fichiers local du worker
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
            String annee = cols[0].split("-")[0];
            return new Tuple2<>(cols[1] + " " + annee, Double.parseDouble(cols[3]));
        }).reduceByKey(Double::sum);
        
        System.out.println("Ventes par ville et année :");
        ventesVilleAnnee.collect().forEach(System.out::println);
        
        sc.close();
    }
}
```

### Analyse du Code

**Initialisation du contexte Spark :**
```java
SparkConf conf = new SparkConf().setAppName("Ventes Cluster");
JavaSparkContext sc = new JavaSparkContext(conf);
```
La configuration ne spécifie pas de master car celui-ci sera fourni via `spark-submit` lors de l'exécution.

**Chargement des données :**
```java
JavaRDD<String> lines = sc.textFile("/opt/ventes.txt");
```
Crée un RDD où chaque élément correspond à une ligne du fichier.

**Transformation MapToPair :**
```java
lines.mapToPair(line -> {
    String[] cols = line.split(" ");
    return new Tuple2<>(cols[1], Double.parseDouble(cols[3]));
})
```
Transforme chaque ligne en paire clé-valeur (ville, montant). Cette opération est distribuée sur tous les workers.

**Réduction ReduceByKey :**
```java
.reduceByKey(Double::sum)
```
Agrège les valeurs ayant la même clé en les additionnant. Spark optimise cette opération en effectuant des réductions partielles localement avant de combiner les résultats.

**Action Collect :**
```java
ventesParVille.collect().forEach(System.out::println);
```
Récupère tous les résultats sur le driver et les affiche.

### Captures d'Exécution - Exercice 1

**Exécution en mode local :**

![Capture d'écran de l'exécution locale](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/ex1_local.png)
![Capture d'écran de l'exécution locale](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/ex1_local2.png)
![Capture d'écran de l'exécution locale](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/ex1_local3.png)

**Exécution sur le cluster :**

![Capture d'écran du terminal PowerShell](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/ex1_spar_master.png)

![Capture d'écran des logs d'exécution](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/ex1_spark_master2.png)
![Capture d'écran des logs d'exécution](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/ex1_spark_master3.png)

![Capture d'écran de l'interface web Spark Master](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/spark_interface.png)

---

## Exercice 2 : Analyse des Logs Apache

### Objectif
Parser et analyser des logs de serveur web Apache pour extraire des statistiques sur le trafic, identifier les erreurs et déterminer les ressources les plus sollicitées.

### Format des Données (`access.log`)

```
127.0.0.1 - - [10/Oct/2025:09:15:32 +0000] "GET /index.html HTTP/1.1" 200 1024 "http://example.com" "Mozilla/5.0"
192.168.1.10 - john [10/Oct/2025:09:17:12 +0000] "POST /login HTTP/1.1" 302 512 "-" "curl/7.68.0"
203.0.113.5 - - [10/Oct/2025:09:19:01 +0000] "GET /docs/report.pdf HTTP/1.1" 404 64 "-" "Mozilla/5.0"
198.51.100.7 - - [10/Oct/2025:09:25:48 +0000] "GET /api/data?id=123 HTTP/1.1" 500 128 "-" "PostmanRuntime/7.26.8"
192.168.1.11 - jane [10/Oct/2025:09:30:05 +0000] "GET /dashboard HTTP/1.1" 200 4096 "http://intranet" "Mozilla/5.0"
127.0.0.1 - - [10/Oct/2025:09:35:00 +0000] "GET /index.html HTTP/1.1" 200 1024 "-" "Mozilla/5.0"
192.168.1.10 - john [10/Oct/2025:09:40:12 +0000] "GET /api/data?id=456 HTTP/1.1" 404 80 "-" "curl/7.68.0"
```

Format standard Apache Combined Log avec IP, utilisateur, date, méthode HTTP, URL, code de statut et taille.

### Code Source (`LogAnalyzerApp.java`)

```java
package ma.enset.exercise2;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogAnalyzerApp {
    // Expression régulière pour parser le format Apache Combined Log
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\S+) \\S+ \\S+ \\[(.*?)\\] \"(\\S+) (\\S+)\\s*.*?\" (\\d{3}) (\\d+|-)"
    );
    
    public static void main(String[] args) {
        SparkConf conf = new SparkConf().setAppName("Log Analyzer Cluster");
        JavaSparkContext sc = new JavaSparkContext(conf);
        
        JavaRDD<String> logLines = sc.textFile("/opt/access.log");
        
        // Parsing des logs avec regex
        JavaRDD<String[]> parsedLogs = logLines.map(line -> {
            Matcher m = LOG_PATTERN.matcher(line);
            if (m.find()) {
                return new String[]{ 
                    m.group(1),  // IP
                    m.group(2),  // Date
                    m.group(3),  // Méthode HTTP
                    m.group(4),  // URL
                    m.group(5),  // Code statut
                    m.group(6)   // Taille
                };
            }
            return null;
        }).filter(x -> x != null).cache();
        
        // Statistiques de base
        long totalReq = parsedLogs.count();
        long totalErrors = parsedLogs.filter(cols -> 
            Integer.parseInt(cols[4]) >= 400
        ).count();
        double errorRate = (double) totalErrors / totalReq * 100;
        
        System.out.println("Total requêtes: " + totalReq);
        System.out.println("Total erreurs: " + totalErrors);
        System.out.println("Taux d'erreur: " + errorRate + "%");
        
        // Top 5 des adresses IP
        System.out.println("Top 5 IP:");
        parsedLogs.mapToPair(cols -> new Tuple2<>(cols[0], 1))
                .reduceByKey(Integer::sum)
                .mapToPair(Tuple2::swap)
                .sortByKey(false)
                .take(5).forEach(System.out::println);
        
        // Top 5 des ressources
        System.out.println("Top 5 Ressources:");
        parsedLogs.mapToPair(cols -> new Tuple2<>(cols[3], 1))
                .reduceByKey(Integer::sum)
                .mapToPair(Tuple2::swap)
                .sortByKey(false)
                .take(5).forEach(System.out::println);
        
        // Répartition par code HTTP
        System.out.println("Répartition par Code HTTP:");
        parsedLogs.mapToPair(cols -> new Tuple2<>(cols[4], 1))
                .reduceByKey(Integer::sum)
                .collect().forEach(System.out::println);
        
        sc.close();
    }
}
```

### Analyse du Code

**Pattern Regex pour le parsing :**
```java
private static final Pattern LOG_PATTERN = Pattern.compile(
    "^(\\S+) \\S+ \\S+ \\[(.*?)\\] \"(\\S+) (\\S+)\\s*.*?\" (\\d{3}) (\\d+|-)"
);
```
Cette expression régulière extrait les champs importants : IP, date, méthode HTTP, URL, code de statut et taille de réponse.

**Transformation Map avec parsing :**
```java
JavaRDD<String[]> parsedLogs = logLines.map(line -> {
    Matcher m = LOG_PATTERN.matcher(line);
    if (m.find()) {
        return new String[]{ m.group(1), m.group(2), ..., m.group(6) };
    }
    return null;
}).filter(x -> x != null).cache();
```
Chaque ligne est parsée et transformée en tableau de chaînes. Les lignes invalides sont filtrées. L'appel à `cache()` garde le RDD en mémoire pour éviter de recalculer le parsing lors des opérations suivantes.

**Calcul du taux d'erreur :**
```java
long totalErrors = parsedLogs.filter(cols -> 
    Integer.parseInt(cols[4]) >= 400
).count();
```
Filtre les requêtes avec un code de statut HTTP supérieur ou égal à 400 (erreurs client et serveur).

**Top 5 des IP avec swap et sort :**
```java
parsedLogs.mapToPair(cols -> new Tuple2<>(cols[0], 1))
        .reduceByKey(Integer::sum)
        .mapToPair(Tuple2::swap)
        .sortByKey(false)
        .take(5)
```
Cette chaîne d'opérations compte les occurrences par IP, inverse les paires pour avoir (count, IP), trie par count décroissant et prend les 5 premiers résultats.

### Captures d'Exécution - Exercice 2

**Exécution en mode local :**

![Capture d'écran de l'exécution locale](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/ex2_local.png)
![Capture d'écran de l'exécution locale](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/ex2_local2.png)
![Capture d'écran de l'exécution locale](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/ex2_local3.png)
![Capture d'écran de l'exécution locale](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/ex2_local4.png)

**Exécution sur le cluster :**

![Capture d'écran de la commande spark-submit](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/ex2_spark_master.png)

![Capture d'écran des logs d'analyse](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/ex2_spark_master2.png)
![Capture d'écran des logs d'analyse](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/ex2_spark_master3.png)
![Capture d'écran des logs d'analyse](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/ex2_spark_master4.png)
![Capture d'écran des logs d'analyse](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/ex2_spark_master5.png)

![Capture d'écran de l'interface web Spark](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/spark_interface.png)

---

## Compilation et Packaging

### Commande Maven

Pour compiler le projet et créer le JAR exécutable avec toutes les dépendances :

```powershell
mvn clean package
```

Cette commande effectue les étapes suivantes :

1. **clean** : Supprime le répertoire `target/` et tous les fichiers compilés précédemment
2. **compile** : Compile les sources Java vers des fichiers `.class`
3. **test** : Exécute les tests unitaires (si présents)
4. **package** : Crée le fichier JAR
5. **shade** : Le plugin Shade combine toutes les dépendances dans un JAR unique

### Structure du répertoire target après compilation

```
target/
├── classes/                                   # Classes compilées
├── maven-archiver/
├── maven-status/
├── original-tp3_rdd-1.0-SNAPSHOT.jar         # JAR sans dépendances
└── tp3_rdd-1.0-SNAPSHOT.jar                  # JAR avec dépendances (Fat JAR)
```

Le fichier `tp3_rdd-1.0-SNAPSHOT.jar` est le Fat JAR contenant toutes les dépendances nécessaires pour l'exécution sur le cluster.

---

![compilation du projet](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/mvn_package.png)
![compilation du projet](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/mvn_package3.png)

## Déploiement sur le Cluster

### Démarrage du cluster Docker

```powershell
docker-compose up -d
```

Cette commande lance tous les conteneurs en arrière-plan. Vérifiez que tous les services sont démarrés :

```powershell
docker ps
```

Vous devriez voir les conteneurs suivants actifs : `spark-master`, `spark-worker-1`, `namenode`, `datanode`, `resourcemanager`, `nodemanager`.

![demarrage de cluster](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/docker_ps.png)

### Copie des fichiers sur les nœuds

**Copie du JAR sur le Master :**

```powershell
docker cp target/tp3_rdd-1.0-SNAPSHOT.jar spark-master:/opt/tp.jar
```

Le Master a besoin du JAR pour soumettre l'application au cluster.
![copie du jar](https://github.com/FatihaELHABTI/tp3_rdd_spark/blob/main/imgs/copie_jar.png)

**Copie des données pour l'Exercice 1 :**

```powershell
docker cp ventes.txt spark-master:/opt/ventes.txt
docker cp ventes.txt spark-worker-1:/opt/ventes.txt
```

Les fichiers de données doivent être présents sur tous les workers qui vont les traiter.

**Copie des données pour l'Exercice 2 :**

```powershell
docker cp access.log spark-master:/opt/access.log
docker cp access.log spark-worker-1:/opt/access.log
```

---

## Exécution et Résultats

### Exécution de l'Exercice 1 (Analyse des Ventes)

```powershell
docker exec -it spark-master /opt/spark/bin/spark-submit `
  --class ma.enset.exercise1.VentesApp `
  --master spark://spark-master:7077 `
  /opt/tp.jar
```

**Paramètres de spark-submit :**

- `--class` : Spécifie la classe Java contenant la méthode `main()`
- `--master` : URL du Spark Master pour soumettre l'application en mode cluster
- Le dernier argument est le chemin vers le JAR

**Résultats attendus :**

```
Ventes par ville :
(Lyon,1900.0)
(Paris,2050.0)
(Casablanca,2470.0)

Ventes par ville et année :
(Casablanca 2023,1650.0)
(Paris 2023,800.0)
(Lyon 2024,1900.0)
(Paris 2024,1250.0)
(Casablanca 2024,820.0)
```

### Exécution de l'Exercice 2 (Analyse des Logs)

```powershell
docker exec -it spark-master /opt/spark/bin/spark-submit `
  --class ma.enset.exercise2.LogAnalyzerApp `
  --master spark://spark-master:7077 `
  /opt/tp.jar
```

**Résultats attendus :**

```
Total requêtes: 7
Total erreurs: 2
Taux d'erreur: 28.57%

Top 5 IP:
(2,127.0.0.1)
(2,192.168.1.10)
(1,203.0.113.5)
(1,198.51.100.7)
(1,192.168.1.11)

Top 5 Ressources:
(2,/index.html)
(2,/api/data?id=123)
(1,/login)
(1,/docs/report.pdf)
(1,/dashboard)

Répartition par Code HTTP:
(200,3)
(302,1)
(404,2)
(500,1)
```


## Commandes Utiles

### Gestion du Cluster Docker

```powershell
# Démarrer le cluster
docker-compose up -d

# Arrêter le cluster
docker-compose down

# Voir les logs d'un conteneur
docker logs spark-master
docker logs spark-worker-1

# Voir tous les conteneurs actifs
docker ps

# Accéder au shell d'un conteneur
docker exec -it spark-master bash

# Redémarrer un conteneur spécifique
docker restart spark-master
```

### Vérification de l'État du Cluster

```powershell
# Vérifier les workers Spark enregistrés
docker exec -it spark-master cat /opt/spark/logs/*

# Vérifier l'état HDFS
docker exec -it namenode hdfs dfsadmin -report

# Vérifier l'état YARN
docker exec -it resourcemanager yarn node -list
```

### Nettoyage et Maintenance

```powershell
# Supprimer tous les conteneurs et volumes
docker-compose down -v

# Nettoyer les images non utilisées
docker system prune -a

# Voir l'utilisation
