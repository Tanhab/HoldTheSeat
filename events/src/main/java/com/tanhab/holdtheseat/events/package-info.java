/**
 * Event DTOs exchanged over Kafka by the booking, seat and payment services.
 *
 * <p>Every type here carries a {@code schemaVersion} and must serialise without a
 * Spring context — this module is a plain library and never depends on service code.
 */
package com.tanhab.holdtheseat.events;
