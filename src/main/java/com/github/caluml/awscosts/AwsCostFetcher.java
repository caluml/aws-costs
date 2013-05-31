package com.github.caluml.awscosts;

import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.Reservation;

public class AwsCostFetcher implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(AwsCostFetcher.class);

	private static final String GRAPHITEHOST = "graphitehost";
	private static final int GRAPHITEPORT = 2003;
	private static final String GRAPHITE_PREFIX = "aws.ec2.instances.costs";

	private static AwsCostFetcher instance = new AwsCostFetcher();

	public static AwsCostFetcher get() {
		return instance;
	}

	private AwsCostFetcher() {
		Thread thread = new Thread(null, this, "name");
		thread.setDaemon(true);
		thread.start();
		logger.info("Instantiated AwsCostFetcher with " + thread);
	}

	@Override
	public void run() {
		logger.info("Started run()");
		while (true) {
			try {
				logger.info("Trying...");
				ClientConfiguration clientConfiguration = new ClientConfiguration();
				logger.info("Created clientConfiguration");
				clientConfiguration.setConnectionTimeout(2000);
				logger.info("Got clientConfiguration");
				AmazonEC2Client ec2Client = new AmazonEC2Client();
				ec2Client.setEndpoint("ec2.eu-west-1.amazonaws.com");
				logger.info("Created " + ec2Client);

				DescribeInstancesResult instancesResult = ec2Client.describeInstances();
				List<Reservation> reservations = instancesResult.getReservations();
				List<Instance> instances = new ArrayList<Instance>();
				for (Reservation reservation : reservations) {
					instances.addAll(reservation.getInstances());
				}

				Map<InstanceType, Integer> counts = new HashMap<InstanceType, Integer>();
				for (Instance instance : instances) {
					InstanceType type = InstanceType.fromValue(instance.getInstanceType());
					Integer count = counts.get(type);
					counts.put(type, count == null ? 1 : ++count);
				}
				processCounts(counts);
			} catch (RuntimeException e) {
				logger.error("", e);
			} finally {
				try {
					logger.info("Sleeping");
					TimeUnit.MINUTES.sleep(1);
					logger.info("Woken");
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	private void processCounts(Map<InstanceType, Integer> counts) {
		logger.info("Counts: " + counts);

		BigDecimal total = BigDecimal.ZERO;
		InstanceCosts costs = new InstanceCosts();
		for (InstanceType type : counts.keySet()) {
			BigDecimal perHour = costs.of(type).in(Regions.EU_WEST_1).with(counts.get(type)).perHour();
			System.out.println(type + ": $" + perHour);
			total = total.add(perHour);
		}
		System.out.println("Total: $" + total);

		sendGraphiteMetrics(counts);
	}

	private void sendGraphiteMetrics(Map<InstanceType, Integer> counts) {
		Socket conn = null;
		try {
			conn = new Socket();
			conn.connect(new InetSocketAddress(GRAPHITEHOST, GRAPHITEPORT), 1000);
			logger.debug("Opened socket " + conn);
			DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
			InstanceCosts costs = new InstanceCosts();
			for (InstanceType type : counts.keySet()) {
				BigDecimal perHour = costs.of(type).in(Regions.EU_WEST_1).with(counts.get(type)).perHour();
				int epoch = (int) (new Date().getTime() / 1000l);
				String data = GRAPHITE_PREFIX + "." + type.toString().replace(".", "_") + " " + perHour + " " + epoch
						+ "\n";
				dos.writeBytes(data);
				logger.debug("Wrote " + data + " to " + GRAPHITEHOST + ":" + GRAPHITEPORT);
			}
		} catch (UnknownHostException e) {
			logger.error("", e);
		} catch (IOException e) {
			logger.error("", e);
		} catch (RuntimeException e) {
			logger.error("", e);
		} finally {
			if (conn != null) {
				try {
					conn.close();
					logger.debug("Opened socket " + conn);
				} catch (IOException e) {
					//
				}
			}
		}
	}
}