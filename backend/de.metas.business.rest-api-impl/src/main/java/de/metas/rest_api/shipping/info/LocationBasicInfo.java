/*
 * #%L
 * de.metas.business.rest-api-impl
 * %%
 * Copyright (C) 2020 metas GmbH
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

package de.metas.rest_api.shipping.info;

import com.google.common.base.Joiner;
import de.metas.common.shipment.JsonLocation;
import de.metas.util.Check;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Value
@Builder
public class LocationBasicInfo
{
	@NonNull
	String countryCode;

	@NonNull
	String city;

	@NonNull
	String postalCode;

	@Nullable
	String streetAndNumber;

	@Nullable
	public static LocationBasicInfo of(@NonNull final JsonLocation location)
	{
		if (Check.isBlank(location.getCountryCode())
				|| Check.isBlank(location.getCity())
		        || Check.isBlank(location.getZipCode()))
		{
			return null;
		}

		final List<String> streetAndHouseNoParts = Stream.of(location.getStreet(), location.getHouseNo())
				.filter(Check::isNotBlank)
				.collect(Collectors.toList());

		final String streetAndHouseNo = !streetAndHouseNoParts.isEmpty()
				? Joiner.on(",").join(streetAndHouseNoParts)
				: null;

		return LocationBasicInfo.builder()
				.countryCode(location.getCountryCode())
				.city(location.getCity())
				.postalCode(location.getZipCode())
				.streetAndNumber(streetAndHouseNo)
				.build();
	}
}
