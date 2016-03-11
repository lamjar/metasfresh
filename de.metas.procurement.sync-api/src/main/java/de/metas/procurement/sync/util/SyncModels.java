package de.metas.procurement.sync.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import de.metas.procurement.sync.protocol.AbstractSyncModel;

/*
 * #%L
 * de.metas.procurement.sync-api
 * %%
 * Copyright (C) 2016 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

/**
 * Helper class used to manipulate {@link AbstractSyncModel}s.
 *
 * @author metas-dev <dev@metas-fresh.com>
 *
 */
public final class SyncModels
{
	public static final Set<String> extractUUIDs(final Collection<? extends AbstractSyncModel> syncModels)
	{
		final Set<String> uuids = new HashSet<>();
		if (syncModels == null || syncModels.isEmpty())
		{
			return uuids;
		}

		for (final AbstractSyncModel syncModel : syncModels)
		{
			if (syncModel == null)
			{
				// shall not happen
				continue;
			}

			final String uuid = syncModel.getUuid();
			if (uuid == null)
			{
				// shall not happen
				continue;
			}

			uuids.add(uuid);
		}

		return uuids;
	}

	private SyncModels()
	{
		super();
	}
}
