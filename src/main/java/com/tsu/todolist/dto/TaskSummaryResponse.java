package com.tsu.todolist.dto;

public record TaskSummaryResponse(long todo, long inProgress, long done, long total) {
}
