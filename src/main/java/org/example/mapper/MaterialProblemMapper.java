package org.example.mapper;

import org.example.MaterialProblem;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface MaterialProblemMapper {

    List<MaterialProblem> findByMaterialId(Integer materialId);

    List<MaterialProblem> findAll();
}
