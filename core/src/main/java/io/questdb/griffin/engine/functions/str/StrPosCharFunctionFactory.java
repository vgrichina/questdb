/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2022 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.griffin.engine.functions.str;

import io.questdb.cairo.CairoConfiguration;
import io.questdb.cairo.sql.Function;
import io.questdb.cairo.sql.Record;
import io.questdb.griffin.FunctionFactory;
import io.questdb.griffin.SqlException;
import io.questdb.griffin.SqlExecutionContext;
import io.questdb.griffin.engine.functions.BinaryFunction;
import io.questdb.griffin.engine.functions.IntFunction;
import io.questdb.griffin.engine.functions.UnaryFunction;
import io.questdb.griffin.engine.functions.constants.CharConstant;
import io.questdb.griffin.engine.functions.constants.IntConstant;
import io.questdb.std.IntList;
import io.questdb.std.Numbers;
import io.questdb.std.ObjList;
import io.questdb.griffin.PlanSink;
import org.jetbrains.annotations.NotNull;

public class StrPosCharFunctionFactory implements FunctionFactory {

    @Override
    public String getSignature() {
        return "strpos(SA)";
    }

    @Override
    public Function newInstance(
            int position,
            ObjList<Function> args,
            IntList argPositions,
            CairoConfiguration configuration,
            SqlExecutionContext sqlExecutionContext
    ) throws SqlException {
        final Function substrFunc = args.getQuick(1);
        if (substrFunc.isConstant()) {
            char substr = substrFunc.getChar(null);
            if (substr == CharConstant.ZERO.getChar(null)) {
                return IntConstant.NULL;
            }
            return new ConstFunc(args.getQuick(0), substr);
        }
        return new Func(args.getQuick(0), substrFunc);
    }

    private static int strpos(@NotNull CharSequence str, char substr) {
        final int strLen = str.length();
        if (strLen < 1) {
            return 0;
        }

        for (int i = 0; i < strLen; i++) {
            if (str.charAt(i) == substr) {
                return i + 1;
            }
        }
        return 0;
    }

    public static class ConstFunc extends IntFunction implements UnaryFunction {

        private final Function strFunc;
        private final char substr;

        public ConstFunc(Function strFunc, char substr) {
            this.strFunc = strFunc;
            this.substr = substr;
        }

        @Override
        public Function getArg() {
            return strFunc;
        }

        @Override
        public int getInt(Record rec) {
            final CharSequence str = this.strFunc.getStr(rec);
            if (str == null) {
                return Numbers.INT_NaN;
            }
            return strpos(str, substr);
        }

        @Override
        public void toPlan(PlanSink sink) {
            sink.put("strpos(").put(strFunc).put(",'").put(substr).put("')");
        }
    }

    public static class Func extends IntFunction implements BinaryFunction {

        private final Function strFunc;
        private final Function substrFunc;

        public Func(Function strFunc, Function substrFunc) {
            this.strFunc = strFunc;
            this.substrFunc = substrFunc;
        }

        @Override
        public int getInt(Record rec) {
            final CharSequence str = this.strFunc.getStr(rec);
            if (str == null) {
                return Numbers.INT_NaN;
            }
            final char substr = this.substrFunc.getChar(rec);
            if (substr == CharConstant.ZERO.getChar(null)) {
                return Numbers.INT_NaN;
            }
            return strpos(str, substr);
        }

        @Override
        public Function getLeft() {
            return strFunc;
        }

        @Override
        public Function getRight() {
            return substrFunc;
        }

        @Override
        public String getSymbol() {
            return "strpos";
        }
    }
}
