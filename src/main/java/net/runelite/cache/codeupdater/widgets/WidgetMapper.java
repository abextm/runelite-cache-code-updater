/*
 * Copyright (c) 2019 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.cache.codeupdater.widgets;

import com.google.common.collect.Sets;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.runelite.cache.codeupdater.mapper.Mapper;
import net.runelite.cache.definitions.InterfaceDefinition;

public class WidgetMapper implements Mapper<InterfaceDefinition>
{
	private InterfaceDefinition a;
	private InterfaceDefinition b;

	private double diff = 0;
	private double total = 0;

	@Override
	public double difference(InterfaceDefinition a, InterfaceDefinition b)
	{
		assert a == null;
		assert b == null;
		this.a = a;
		this.b = b;

		if (!a.isIf3 || !b.isIf3)
		{
			// Jagex has no ability to update if1, so we don't need to map it
			double d = 0.d;
			try
			{
				Field[] fs = InterfaceDefinition.class.getFields();
				for (Field f : fs)
				{
					if (!Objects.equals(f.get(a), f.get(b)))
					{
						d += 1.d / (double) fs.length;
					}
				}
			}
			catch (ReflectiveOperationException e)
			{
				throw new RuntimeException(e);
			}
			return d;
		}

		if (a.type != b.type)
		{
			return 1;
		}
		int type = a.type;

		test(InterfaceDefinition::getContentType);
		test(InterfaceDefinition::getOriginalX);
		test(InterfaceDefinition::getOriginalY);
		test(InterfaceDefinition::getOriginalWidth);
		test(InterfaceDefinition::getOriginalHeight);
		test(InterfaceDefinition::getWidthMode);
		test(InterfaceDefinition::getHeightMode);
		test(InterfaceDefinition::getXPositionMode);
		test(InterfaceDefinition::getYPositionMode);
		test(InterfaceDefinition::getParentId);
		test(InterfaceDefinition::isHidden);

		if (type == 0)
		{
			test(InterfaceDefinition::getScrollWidth);
			test(InterfaceDefinition::getScrollHeight);
			test(InterfaceDefinition::isNoClickThrough);
		}

		if (type == 5)
		{
			test(InterfaceDefinition::getSpriteId);
			test(InterfaceDefinition::getTextureId);
			test(InterfaceDefinition::isSpriteTiling);
			test(InterfaceDefinition::getOpacity);
			test(InterfaceDefinition::getBorderType);
			test(InterfaceDefinition::getShadowColor);
			test(InterfaceDefinition::isFlippedVertically);
			test(InterfaceDefinition::isFlippedHorizontally);
		}

		if (type == 6)
		{
			test(InterfaceDefinition::getModelType);
			test(InterfaceDefinition::getModelId);
			test(InterfaceDefinition::getOffsetX2d);
			test(InterfaceDefinition::getOffsetY2d);
			test(InterfaceDefinition::getRotationX);
			test(InterfaceDefinition::getRotationZ);
			test(InterfaceDefinition::getRotationY);
			test(InterfaceDefinition::getModelZoom);
			test(InterfaceDefinition::getAnimation);
			test(InterfaceDefinition::isOrthogonal);
			test(InterfaceDefinition::getModelHeightOverride);
		}

		if (type == 4)
		{
			test(InterfaceDefinition::getFontId);
			test(InterfaceDefinition::getLineHeight);
			test(InterfaceDefinition::getXTextAlignment);
			test(InterfaceDefinition::getYTextAlignment);
			test(InterfaceDefinition::isTextShadowed);
			test(InterfaceDefinition::getTextColor);
		}

		if (type == 3)
		{
			test(InterfaceDefinition::getTextColor);
			test(InterfaceDefinition::isFilled);
			test(InterfaceDefinition::getOpacity);
		}

		if (type == 9)
		{
			test(InterfaceDefinition::getLineWidth);
			test(InterfaceDefinition::getLineHeight);
			test(InterfaceDefinition::isLineDirection);
		}

		test(InterfaceDefinition::getClickMask);
		test(InterfaceDefinition::getName);

		testActions();

		test(InterfaceDefinition::getDragDeadZone);
		test(InterfaceDefinition::getDragDeadTime);
		test(InterfaceDefinition::isDragRenderBehavior);
		test(InterfaceDefinition::getTargetVerb);

		testListener(InterfaceDefinition::getOnLoadListener);
		testListener(InterfaceDefinition::getOnMouseOverListener);
		testListener(InterfaceDefinition::getOnMouseLeaveListener);
		testListener(InterfaceDefinition::getOnTargetLeaveListener);
		testListener(InterfaceDefinition::getOnTargetEnterListener);
		testListener(InterfaceDefinition::getOnVarTransmitListener);
		testListener(InterfaceDefinition::getOnInvTransmitListener);
		testListener(InterfaceDefinition::getOnScrollWheelListener);
		testListener(InterfaceDefinition::getOnTimerListener);
		testListener(InterfaceDefinition::getOnOpListener);
		testListener(InterfaceDefinition::getOnMouseRepeatListener);
		testListener(InterfaceDefinition::getOnClickListener);
		testListener(InterfaceDefinition::getOnClickRepeatListener);
		testListener(InterfaceDefinition::getOnReleaseListener);
		testListener(InterfaceDefinition::getOnHoldListener);
		testListener(InterfaceDefinition::getOnDragListener);
		testListener(InterfaceDefinition::getOnDragCompleteListener);
		testListener(InterfaceDefinition::getOnScrollWheelListener);

		testTrigger(InterfaceDefinition::getVarTransmitTriggers);
		testTrigger(InterfaceDefinition::getInvTransmitTriggers);
		testTrigger(InterfaceDefinition::getStatTransmitTriggers);

		return diff / total;
	}

	private void testListener(Function<InterfaceDefinition, Object[]> fn)
	{
		total += .2;
		Object[] ab = fn.apply(a);
		Object[] bb = fn.apply(b);

		if (ab == null && bb == null)
		{
			return;
		}
		total += 1.8;
		if (ab == null || bb == null)
		{
			diff += 2;
			return;
		}

		if (!Objects.equals(ab[0], bb[0]))
		{
			diff += 1;
		}
		if (!Arrays.deepEquals(ab, bb))
		{
			diff += 1;
		}
	}

	private void testActions()
	{
		total += 1;
		String[] aa = a.getActions();
		String[] ba = a.getActions();

		if (aa == null && ba == null)
		{
			return;
		}

		if (aa == null || ba == null)
		{
			diff += 1;
			return;
		}

		Set<String> as = Stream.of(aa).collect(Collectors.toSet());
		Set<String> bs = Stream.of(ba).collect(Collectors.toSet());

		int delta = Sets.symmetricDifference(as, bs).size();
		as.addAll(bs);
		diff += delta / (double) as.size();
	}

	private void testTrigger(Function<InterfaceDefinition, int[]> fn)
	{
		total += .5;
		int[] ab = fn.apply(a);
		int[] bb = fn.apply(b);

		if (ab == null && bb == null)
		{
			return;
		}

		total += .5;
		if (ab == null || bb == null)
		{
			diff += 1;
			return;
		}
		Set<Integer> as = IntStream.of(ab).boxed().collect(Collectors.toSet());
		Set<Integer> bs = IntStream.of(bb).boxed().collect(Collectors.toSet());

		int delta = Sets.symmetricDifference(as, bs).size();
		as.addAll(bs);
		diff += delta / (double) as.size();
	}

	private void test(Function<InterfaceDefinition, Object> fn)
	{
		total += 1;
		if (!Objects.equals(fn.apply(a), fn.apply(b)))
		{
			diff += 1;
		}
	}
}
