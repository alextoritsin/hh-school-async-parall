package ru.hh.school.homework;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Stream;

import static java.util.Collections.reverseOrder;
import static java.util.Map.Entry.comparingByValue;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

public class FolderProcessor extends RecursiveTask<List<Map<String, Long>>> {
  public final Path path;

  public FolderProcessor(Path path) {
    this.path = path;
  }

  @Override
  protected List<Map<String, Long>> compute() {
    File file = new File(this.path.toString());

    if (file.isFile() && file.getName().endsWith(".java")) {
      return List.of(naiveCount(file.toPath()));
    } else if (file.isDirectory()) {
      List<ForkJoinTask<List<Map<String, Long>>>> forks = new ArrayList<>();

      for (File directoryItem : Objects.requireNonNull(file.listFiles())) {
        forks.add(new FolderProcessor(directoryItem.toPath()).fork());
      }

      List<Map<String, Long>> listOfWordMaps = new ArrayList<>();

      for (ForkJoinTask<List<Map<String, Long>>> task : forks) {
        listOfWordMaps.addAll(task.join());
      }
      return listOfWordMaps;

    } else return new ArrayList<>();
  }

  private static Map<String, Long> naiveCount(Path path) {
    try (Stream<String> lines = Files.lines(path)) {
      return lines.parallel()
        .flatMap(line -> Stream.of(line.split("[^a-zA-Z0-9]")))
        .filter(word -> word.length() > 3)
        .collect(groupingBy(identity(), counting()))
        .entrySet()
        .stream()
        .sorted(comparingByValue(reverseOrder()))
        .limit(Launcher.FOLDER_TOP_TEN_WORDS)
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
