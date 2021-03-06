/**
 * AntiXRay Server Plugin for Minecraft
 * Copyright (C) 2012 Ryan Hamshire
 * Copyright (C) blablubbabc <http://www.blablubbabc.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package me.ryanhamshire.AntiXRay;

public abstract class Callback<T> implements Runnable {

	protected T result = null;

	@Override
	public void run() {
		this.onComplete(this.getResult());
	}

	public Callback<T> setResult(final T result) {
		this.result = result;
		return this;
	}

	public T getResult() {
		return result;
	}

	protected abstract void onComplete(T result);
}
