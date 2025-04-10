package pt.unl.fct.di.apdc.firstwebapp.util;

import com.google.cloud.Timestamp;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class AuthToken {

	public static final long EXPIRATION_TIME = 1000*60*60*2;
	
	public String username;
	public String role;
	public Timestamp validFrom;
	public Timestamp validUntil;
	public String token;
	
	public AuthToken() {}
	
	public AuthToken(String username, String role) {
		this.username = username;
		this.role = role;
		this.validFrom = Timestamp.now();
		Instant validFromInstant = validFrom.toDate().toInstant();
		Instant validUntilInstant = validFromInstant.plus(2, ChronoUnit.HOURS);
		this.validUntil = Timestamp.ofTimeSecondsAndNanos(validUntilInstant.getEpochSecond(), validUntilInstant.getNano());
		this.token = UUID.randomUUID().toString();
	}
	
	
}
