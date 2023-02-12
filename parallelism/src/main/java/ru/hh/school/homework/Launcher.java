package ru.hh.school.homework;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import static java.util.Collections.reverseOrder;
import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toMap;

public class Launcher {
  public static final short FOLDER_TOP_TEN_WORDS = 10;
  private static final Map<String, Long> searchCache = new ConcurrentHashMap<>();
  public static void main(String[] args) throws IOException {
    // Написать код, который, как можно более параллельно:
    // - по заданному пути найдет все "*.java" файлы
    // - для каждого файла вычислит 10 самых популярных слов (см. #naiveCount())
    // - соберет top 10 для каждой папки в которой есть хотя-бы один java файл
    // - для каждого слова сходит в гугл и вернет количество результатов по нему (см. #naiveSearch())
    // - распечатает в консоль результаты в виде:
    // <папка1> - <слово #1> - <кол-во результатов в гугле>
    // <папка1> - <слово #2> - <кол-во результатов в гугле>
    // ...
    // <папка1> - <слово #10> - <кол-во результатов в гугле>
    // <папка2> - <слово #1> - <кол-во результатов в гугле>
    // <папка2> - <слово #2> - <кол-во результатов в гугле>
    // ...
    // <папка2> - <слово #10> - <кол-во результатов в гугле>
    // ...
    //
    // Порядок результатов в консоли не обязательный.
    // При желании naiveSearch и naiveCount можно оптимизировать.
//    testSearch();

    testCount();
  }

  private static void testCount() throws IOException {
    Path testDir = Path.of(System.getProperty("user.dir"));

    ExecutorService searchExecutor = Executors.newFixedThreadPool(
      Runtime.getRuntime().availableProcessors());

    List<CompletableFuture<Void>> futures = Collections.synchronizedList(new ArrayList<>());

    List<Path> folderPaths = Files.walk(testDir)
      .filter(Files::isDirectory)
      .toList();

    for (Path path : folderPaths) {
      try (ForkJoinPool pool = new ForkJoinPool()) {
        FolderProcessor task = new FolderProcessor(path);

        Map<String, Long> folderTopWords = pool.invoke(task)
          .stream()
          .flatMap(map -> map.entrySet().stream())
          .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum))
          .entrySet()
          .stream()
          .sorted(comparingByValue(reverseOrder()))
          .limit(FOLDER_TOP_TEN_WORDS)
          .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        for (String word : folderTopWords.keySet()) {
          CompletableFuture<Void> future = CompletableFuture
            .supplyAsync(() -> naiveSearch(word), searchExecutor)
            .thenAccept(searchResult -> System.out.printf(
              """
                Folder: %s/%s
                Word: %s
                Search result: %d
                ------------------
                """,
               path.getName(path.getNameCount() - 2), path.getFileName(), word, searchResult));

          futures.add(future);
        }
      }
    }
    waitAndCompleteThreads(searchExecutor, futures);
  }

  private static void waitAndCompleteThreads(ExecutorService pool, List<CompletableFuture<Void>> futures) {
    try {
      CompletableFuture.allOf(futures.toArray(
        new CompletableFuture[futures.size()]))
        .get(10, TimeUnit.SECONDS);
    } catch (ExecutionException | TimeoutException |InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    pool.shutdown();
  }

  private static void testSearch() {
    System.out.println(naiveSearch("public"));
  }

  private static long naiveSearch(String query) {
    if (searchCache.containsKey(query)) {
      return searchCache.get(query);
    }

    Document document = null;
    try {
      document = Jsoup //
        .connect("https://www.google.com/search?q=" + query) //
        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.116 Safari/537.36") //
        .get();
    } catch (IOException e) {
      e.printStackTrace();
    }

    Element divResultStats = document.select("div#slim_appbar").first();
    String text = divResultStats.text();
    String resultsPart = text.substring(0, text.indexOf('('));
    long result = Long.parseLong(resultsPart.replaceAll("[^0-9]", ""));

    searchCache.put(query, result);
    return result;
  }

}
