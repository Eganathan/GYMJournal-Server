package dev.eknath.GymJournal

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GymJournalApplication

fun main(args: Array<String>) {
	runApplication<GymJournalApplication>(*args)
}
