package de.metas.banking.payment.paymentallocation.service;

import java.math.BigDecimal;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import de.metas.allocation.api.PaymentAllocationId;
import de.metas.money.Money;
import de.metas.util.OptionalDeferredException;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/*
 * #%L
 * de.metas.banking.base
 * %%
 * Copyright (C) 2019 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

@Value
@Builder
public class PaymentAllocationResult
{
	@NonNull
	private ImmutableList<AllocationLineCandidate> candidates;
	@NonNull
	private OptionalDeferredException<PaymentAllocationException> fullyAllocatedCheck;
	@NonNull
	private final ImmutableSet<PaymentAllocationId> paymentAllocationIds;

	public boolean isOK()
	{
		return fullyAllocatedCheck.isNoError();
	}

	public BigDecimal getTotalDiscountAmtAsBigDecimal()
	{
		return getTotalDiscountAmt().map(Money::toBigDecimal).orElse(BigDecimal.ZERO);
	}

	public Optional<Money> getTotalDiscountAmt()
	{
		return candidates.stream()
				.map(line -> line.getAmounts().getDiscountAmt())
				.reduce(Money::add);
	}

	public BigDecimal getTotalWriteOffAmtAsBigDecimal()
	{
		return getTotalWriteOffAmt().map(Money::toBigDecimal).orElse(BigDecimal.ZERO);
	}

	public Optional<Money> getTotalWriteOffAmt()
	{
		return candidates.stream()
				.map(line -> line.getAmounts().getWriteOffAmt())
				.reduce(Money::add);
	}
}
