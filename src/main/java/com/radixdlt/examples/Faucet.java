package com.radixdlt.examples;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.dapps.messaging.RadixMessaging;
import com.radixdlt.client.dapps.wallet.RadixWallet;
import io.reactivex.Completable;
import io.reactivex.Single;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A service which sends tokens to whoever sends it a message through
 * a Radix Universe.
 */
public class Faucet {

	/**
	 * The amount of time a requestor must wait to make subsequent token requests
	 */
	private final static long DELAY = 1000 * 60 * 10; //10min

	private final RadixApplicationAPI api;
	private final RadixMessaging messaging;
	private final RadixWallet wallet;

	/**
	 * A faucet created on the default universe
	 *
	 * @param api
	 */
	private Faucet(RadixApplicationAPI api) {
		this.api = api;
		this.messaging = new RadixMessaging(api);
		this.wallet = new RadixWallet(api);
	}

	/**
	 * Send XRD from this account to an address
	 *
	 * @param to the address to send to
	 * @return completable whether transfer was successful or not
	 */
	private Completable leakFaucet(RadixAddress to) {
		return this.wallet.send(new BigDecimal(10), to)
			.toObservable()
			.doOnNext(state -> System.out.println("Transaction: " + state))
			.filter(AtomSubmissionUpdate::isComplete)
			.firstOrError()
			.flatMapCompletable(update -> update.getState() == AtomSubmissionState.STORED ?
				Completable.complete() : Completable.error(new RuntimeException(update.toString()))
			);
	}

	/**
	 * Actually send a reply message to the requestor through the Universe
	 *
	 * @param message message to send back
	 * @param to address to send message back to
	 * @return state of the message atom submission
	 */
	private Completable sendReply(String message, RadixAddress to) {
		return messaging.sendMessage(message, to)
			.toCompletable()
			.doOnComplete(() -> System.out.println("Sent reply"));
	}

	/**
	 * Start and run the faucet service
	 */
	public void run() {
		final RadixAddress sourceAddress = api.getMyAddress();

		System.out.println("Faucet Address: " + sourceAddress);

		// Print out current balance of faucet
		wallet.getBalance()
			.subscribe(
				balance -> System.out.println("Faucet Balance: " + balance),
				Throwable::printStackTrace
			)
		;

		api.getData(api.getMyAddress()).subscribe(System.out::println, Throwable::printStackTrace);

		// Flow Logic
		// Listen to any recent messages, send 10 XRD to the sender and then send a confirmation whether it succeeded or not
		// NOTE: this is neither idempotent nor atomic!
		messaging
			.getAllMessagesGroupedByParticipants()
			.subscribe(observableByAddress -> {
				final RadixAddress from = observableByAddress.getKey();
				final RateLimiter rateLimiter = new RateLimiter(DELAY);

				observableByAddress
					.doOnNext(System.out::println) // Print out all messages
					.filter(message -> !message.getFrom().equals(sourceAddress)) // Don't send ourselves money
					.filter(message -> Math.abs(message.getTimestamp() - System.currentTimeMillis()) < 60000) // Only deal with recent messages
					.flatMapSingle(message -> {
						if (rateLimiter.check()) {
							return this.leakFaucet(from)
								.doOnComplete(rateLimiter::reset)
								.andThen(Single.just("Sent you 10 Test Rads!"))
								.onErrorReturn(throwable -> "Couldn't send you any (Reason: " + throwable.getMessage() + ")");
						} else {
							return Single.just(
								"Don't be hasty! You can only make one request every 10 minutes. "
								+ rateLimiter.getTimeLeftString() + " left."
							);
						}
					}, true)
					.flatMapCompletable(msg -> this.sendReply(msg, from))
					.subscribe();
			});
	}

	/**
	 * Simple Rate Limiter helper class
	 */
	private static class RateLimiter {
		private final AtomicLong lastTimestamp = new AtomicLong();
		private final long millis;

		private RateLimiter(long millis) {
			this.millis = millis;
		}

		String getTimeLeftString() {
			long timeSince = System.currentTimeMillis() - lastTimestamp.get();
			long secondsTimeLeft = ((DELAY - timeSince) / 1000) % 60;
			long minutesTimeLeft = ((DELAY - timeSince) / 1000) / 60;
			return minutesTimeLeft + " minutes and " + secondsTimeLeft + " seconds";
		}

		boolean check() {
			return lastTimestamp.get() == 0 || (System.currentTimeMillis() - lastTimestamp.get() > millis);
		}

		void reset() {
			lastTimestamp.set(System.currentTimeMillis());
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.out.println("Usage: java com.radixdlt.client.services.Faucet <highgarden|sunstone|winterfell|winterfell_local> <keyfile> <password>");
			System.exit(-1);
		}

		String universeString = args[0];
		String keyFile = args[1];
		String password = args[2];

		RadixUniverse.bootstrap(Bootstrap.valueOf(universeString.toUpperCase()));

		RadixUniverse.getInstance()
			.getNetwork()
			.getStatusUpdates()
			.subscribe(System.out::println);

		final RadixIdentity faucetIdentity = RadixIdentities.loadOrCreateEncryptedFile(keyFile, password);
		final RadixApplicationAPI api = RadixApplicationAPI.create(faucetIdentity);
		Faucet faucet = new Faucet(api);
		faucet.run();
	}
}
