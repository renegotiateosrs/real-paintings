package com.realpaintings;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FitMode
{
	CROP("Crop to fill"),
	FIT("Fit inside"),
	STRETCH("Stretch");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
