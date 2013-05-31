package com.github.caluml.awscosts;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.model.InstanceType;

public class InstanceCosts {

	private BigDecimal num;
	private InstanceType type;
	private Regions region;

	public InstanceCosts with(int num) {
		this.num = new BigDecimal(String.valueOf(num));
		return this;
	}

	public InstanceCosts of(InstanceType type) {
		this.type = type;
		return this;
	}

	public InstanceCosts in(Regions region) {
		this.region = region;
		return this;
	}

	public BigDecimal perHour() {
		BigDecimal perHour = getCost(region, type);
		return perHour.multiply(num);
	}

	public BigDecimal perMonth() {
		return perHour().multiply(new BigDecimal("744"));
	}

	private BigDecimal getCost(Regions region2, InstanceType type) {
		Map<InstanceType, BigDecimal> regionPrices = getRegionPrices(region2);
		BigDecimal cost = regionPrices.get(type);
		if (cost == null) {
			throw new IllegalArgumentException("InstanceType " + type + " not found");
		}
		return cost.multiply(num);
	}

	private Map<InstanceType, BigDecimal> getRegionPrices(Regions region) {

		Map<InstanceType, BigDecimal> prices = new HashMap<InstanceType, BigDecimal>();
		prices.put(InstanceType.T1Micro, new BigDecimal("0.020"));
		prices.put(InstanceType.M1Small, new BigDecimal("0.065"));
		prices.put(InstanceType.M1Medium, new BigDecimal("0.130"));
		prices.put(InstanceType.M1Large, new BigDecimal("0.260"));
		prices.put(InstanceType.M1Xlarge, new BigDecimal("0.520"));

		Map<Regions, Map<InstanceType, BigDecimal>> regions = new HashMap<Regions, Map<InstanceType, BigDecimal>>();
		regions.put(Regions.EU_WEST_1, prices);

		Map<InstanceType, BigDecimal> map = regions.get(region);

		if (map != null) {
			return map;
		} else {
			throw new IllegalArgumentException("Region " + region + " not supported");
		}
	}
}
