package br.edu.univasf.engrenar.model;
import java.time.LocalDateTime;
public record HistoryEntry(LocalDateTime time,String actor,String action,String details){}
