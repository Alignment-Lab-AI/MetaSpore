//
// Copyright 2022 DMetaSoul
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.dmetasoul.metaspore.recommend.functions;


import com.dmetasoul.metaspore.recommend.annotation.TransformFunction;
import com.dmetasoul.metaspore.recommend.enums.DataTypeEnum;
import com.dmetasoul.metaspore.serving.FeatureTable;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.arrow.vector.FieldVector;

import java.util.List;
import java.util.Map;

@Slf4j
@TransformFunction("bucketize")
public class BucketizeFunction extends Function {
    private final static String NAMEBINS = "bins";
    private final static String NAMEMIN = "min";
    private final static String NAMEMAX = "max";
    private final static String NAMERANGES = "ranges";

    private int bins = 10;
    private int min = 0;
    private int max = 120;

    private List<Integer> ranges = Lists.newArrayList();

    @Override
    public void init(Map<String, Object> params) {
    }

    @Override
    public List<Object> process(List<List<Object>> values, List<DataTypeEnum> types, Map<String, Object> options) {
        return null;
    }
}
