package com.mochat.mochat.service.emp;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mochat.mochat.common.model.PageModel;
import com.mochat.mochat.common.util.wm.ApiRespUtils;
import com.mochat.mochat.dao.entity.WorkDeptEntity;
import com.mochat.mochat.dao.entity.WorkEmployeeDepartmentEntity;
import com.mochat.mochat.dao.entity.WorkEmployeeEntity;
import com.mochat.mochat.dao.entity.permission.McRbacRoleEntity;
import com.mochat.mochat.dao.entity.permission.McRbacUserRoleEntity;
import com.mochat.mochat.dao.mapper.WorkEmployeeDepartmentMapper;
import com.mochat.mochat.model.dept.DeptPageItemVO;
import com.mochat.mochat.model.dept.ReqDeptPageDTO;
import com.mochat.mochat.service.AccountService;
import com.mochat.mochat.service.permission.IRbacRoleService;
import com.mochat.mochat.service.permission.IRbacUserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author zhaojinjian
 * @ClassName WorkEmployeeDepartmentServiceImpl.java
 * @Description TODO
 * @createTime 2021/1/7 13:09
 */
@Service
public class WorkEmployeeDepartmentServiceImpl extends ServiceImpl<WorkEmployeeDepartmentMapper, WorkEmployeeDepartmentEntity> implements IWorkEmployeeDepartmentService {

    @Autowired
    private IWorkEmployeeService workEmployeeService;

    @Autowired
    private IWorkDeptService workDeptService;

    @Autowired
    private IRbacUserRoleService rbacUserRoleService;

    @Autowired
    private IRbacRoleService rbacRoleService;


    @Override
    public List<WorkEmployeeDepartmentEntity> getDeptEmployeeList(List<Integer> deptIds) {
        QueryWrapper<WorkEmployeeDepartmentEntity> employeeDepartmentWrapper = new QueryWrapper<>();
        employeeDepartmentWrapper.in("department_id", deptIds);
        employeeDepartmentWrapper.isNull("deleted_at");
        return this.baseMapper.selectList(employeeDepartmentWrapper);
    }

    /**
     * @author: yangpengwei
     * @time: 2021/3/17 10:55 上午
     * @description 获取员工所在部门与子部门所有的成员 id 列表
     */
    @Override
    public List<Integer> getDeptAndChildDeptEmpIdList() {
        // 查询员工所属的部门 id 列表
        List<Integer> idList = lambdaQuery()
                .select(WorkEmployeeDepartmentEntity::getDepartmentId)
                .eq(WorkEmployeeDepartmentEntity::getEmployeeId, AccountService.getEmpId())
                .list()
                .stream()
                .map(WorkEmployeeDepartmentEntity::getDepartmentId)
                .collect(Collectors.toList());

        // 查询部门及子部门 id 列表
        LambdaQueryChainWrapper<WorkDeptEntity> deptWrapper = workDeptService.lambdaQuery();
        deptWrapper.select(WorkDeptEntity::getId);
        int size = idList.size();
        for (int i = 0; i < size; i++) {
            deptWrapper.like(WorkDeptEntity::getPath, idList.get(i));
            if (i != size - 1) {
                deptWrapper.or();
            }
        }

        // 部门及子部门 id 列表
        idList = deptWrapper.list().stream().map(WorkDeptEntity::getId).collect(Collectors.toList());

        // 查询部门和子部门下的员工 id 列表
        idList = lambdaQuery()
                .select(WorkEmployeeDepartmentEntity::getEmployeeId)
                .in(WorkEmployeeDepartmentEntity::getDepartmentId, idList)
                .groupBy(WorkEmployeeDepartmentEntity::getEmployeeId)
                .list()
                .stream()
                .map(WorkEmployeeDepartmentEntity::getEmployeeId)
                .collect(Collectors.toList());

        return idList;
    }

    @Override
    public List<WorkEmployeeDepartmentEntity> getWorkEmployeeDepartmentsByEmployeeIds(List<String> employeeIdList, String clStr) {
        QueryWrapper<WorkEmployeeDepartmentEntity> workEmployeeDepartmentEntityQueryWrapper = new QueryWrapper();
        workEmployeeDepartmentEntityQueryWrapper.select(clStr);
        workEmployeeDepartmentEntityQueryWrapper.in("employee_id", employeeIdList);
        return this.baseMapper.selectList(workEmployeeDepartmentEntityQueryWrapper);
    }

    @Override
    public Map<String, Object> handleShowEmpData(Map<String, Object> map) {
        //处理请求参数
        String page = !map.get("page").equals("") ? map.get("page").toString() : "1";
        String perPage = !map.get("perPage").equals("") ? map.get("perPage").toString() : "10";
        List<WorkEmployeeEntity> workEmployeeEntityList = null;
        //根据部门Id搜索
        if (!map.get("departmentId").equals("")) {
            //根据部门获取成员id
            List<Integer> departmentIdList = new ArrayList();
            departmentIdList.add(Integer.valueOf(map.get("departmentId").toString()));
            List<WorkEmployeeDepartmentEntity> workEmployeeDepartmentEntityList = getDeptEmployeeList(departmentIdList);
            if(workEmployeeDepartmentEntityList != null && workEmployeeDepartmentEntityList.size() > 0){
                StringBuilder sb = new StringBuilder();
                String empId = null;
                for (WorkEmployeeDepartmentEntity workEmployeeDepartmentEntity :
                        workEmployeeDepartmentEntityList) {
                    empId = workEmployeeDepartmentEntity.getEmployeeId().toString();
                    sb.append(empId).append(",");
                }
                String empIdArr = sb.substring(0, sb.length() - 1);
                //查询数据
                String clStr = "id,log_user_id,name,mobile";
                workEmployeeEntityList = workEmployeeService.getWorkEmployeeList(page, perPage, clStr, empIdArr);
            }
            int totalPageNum = (workEmployeeEntityList == null ? 0 :workEmployeeEntityList.size() + Integer.valueOf(perPage) - 1) / Integer.valueOf(perPage);
            map.put("page", new PageModel(Integer.valueOf(perPage), workEmployeeEntityList == null ? 0 :workEmployeeEntityList.size(), totalPageNum));
        } else {
            String clStr = "id,log_user_id,name,mobile";
            workEmployeeEntityList = workEmployeeService.getWorkEmployeeList(page, perPage, clStr, null);
            int totalPageNum = (workEmployeeEntityList == null ? 0 :workEmployeeEntityList.size() + Integer.valueOf(perPage) - 1) / Integer.valueOf(perPage);
            map.put("page", new PageModel(Integer.valueOf(perPage), workEmployeeEntityList == null ? 0 :workEmployeeEntityList.size(), totalPageNum));
        }
        //数据处理
        List<Map<String, Object>> mapList = null;
        if(workEmployeeEntityList != null && workEmployeeEntityList.size() > 0){
            Map<String, Object> mapData = null;
            mapList = new ArrayList();
            for (WorkEmployeeEntity workEmployeeEntity :
                    workEmployeeEntityList) {
                //根据用户id-获取角色名称
                Integer userId = workEmployeeEntity.getId();
                LambdaQueryChainWrapper<McRbacUserRoleEntity> mcRbacUserRoleWrapper = rbacUserRoleService.lambdaQuery();
                mcRbacUserRoleWrapper.eq(McRbacUserRoleEntity::getUserId,userId);
                mcRbacUserRoleWrapper.select(McRbacUserRoleEntity::getRoleId);
                McRbacUserRoleEntity mcRbacUserRoleEntity = mcRbacUserRoleWrapper.one();
                McRbacRoleEntity mcRbacRoleEntity = null;
                if(mcRbacUserRoleEntity != null){
                    LambdaQueryChainWrapper<McRbacRoleEntity> mcRbacRoleWrapper = rbacRoleService.lambdaQuery();
                    mcRbacRoleWrapper.eq(McRbacRoleEntity::getId,mcRbacUserRoleEntity.getRoleId());
                    mcRbacRoleEntity = mcRbacRoleWrapper.one();
                }
                mapData = new HashMap<String, Object>();
                mapData.put("employeeId", workEmployeeEntity.getId());
                mapData.put("employeeName", workEmployeeEntity.getName());
                mapData.put("phone", workEmployeeEntity.getMobile());
                mapData.put("roleName", mcRbacRoleEntity != null ? mcRbacRoleEntity.getName() : null);
                mapList.add(mapData);
            }

        }
        map.put("list", mapList);
        return map;
    }

    @Override
    public Page<DeptPageItemVO> handlePageIndexData(ReqDeptPageDTO req) {
        Page<WorkDeptEntity> page = ApiRespUtils.initPage(req);
        List<DeptPageItemVO> voList = new ArrayList<>();

        List<String> deptIdListReq = new ArrayList<>();
        List<String> pathList = new ArrayList<>();

        if (StringUtils.hasLength(req.getParentName())) {
            List<Integer> deptIdList = workDeptService.lambdaQuery()
                    .select(WorkDeptEntity::getId)
                    .eq(WorkDeptEntity::getCorpId, AccountService.getCorpId())
                    .gt(WorkDeptEntity::getLevel, 0)
                    .like(WorkDeptEntity::getName, req.getParentName())
                    .list()
                    .stream()
                    .map(WorkDeptEntity::getId)
                    .collect(Collectors.toList());
            if (deptIdList.isEmpty()) {
                return ApiRespUtils.transPage(page, voList);
            }

            for (Integer integer : deptIdList) {
                pathList.add(integer.toString());
            }
        }

        if (StringUtils.hasLength(req.getName())) {
            List<Integer> deptIdList = workDeptService.lambdaQuery()
                    .select(WorkDeptEntity::getId)
                    .eq(WorkDeptEntity::getCorpId, AccountService.getCorpId())
                    .gt(WorkDeptEntity::getLevel, 0)
                    .like(WorkDeptEntity::getName, req.getName())
                    .list()
                    .stream()
                    .map(WorkDeptEntity::getId)
                    .collect(Collectors.toList());
            if (deptIdList.isEmpty()) {
                return ApiRespUtils.transPage(page, voList);
            }

            if (pathList.isEmpty()) {
                for (Integer integer : deptIdList) {
                    pathList.add(integer.toString());
                }
            } else {
                List<String> pathList2 = new ArrayList<>();
                for (String s : pathList) {
                    for (Integer integer : deptIdList) {
                        pathList2.add(s + "%" + integer);
                    }
                }
                pathList.clear();
                pathList.addAll(pathList2);
            }
        }

        if (!pathList.isEmpty()) {
            LambdaQueryChainWrapper<WorkDeptEntity> wrapper = workDeptService.lambdaQuery()
                    .select(WorkDeptEntity::getPath)
                    .eq(WorkDeptEntity::getCorpId, AccountService.getCorpId())
                    .gt(WorkDeptEntity::getLevel, 0);
            int size = pathList.size();
            for (int i = 0; i < size; i++) {
                wrapper.like(WorkDeptEntity::getPath, pathList.get(i));
                if (i != size - 1) {
                    wrapper.or();
                }
            }
            List<String> deptPathList = wrapper.list().stream()
                    .map(WorkDeptEntity::getPath)
                    .collect(Collectors.toList());
            for (String s : deptPathList) {
                s = s.replaceAll("#", "");
                s = s.split("-")[1];
                if (!deptIdListReq.contains(s)) {
                    deptIdListReq.add(s);
                }
            }
            if (deptIdListReq.isEmpty()) {
                return ApiRespUtils.transPage(page, voList);
            }
        }

        LambdaQueryChainWrapper<WorkDeptEntity> wrapper = workDeptService.lambdaQuery()
                .select(WorkDeptEntity::getId)
                .eq(WorkDeptEntity::getCorpId, AccountService.getCorpId())
                .eq(WorkDeptEntity::getLevel, 1);
        if (!deptIdListReq.isEmpty()) {
            wrapper.in(WorkDeptEntity::getId, deptIdListReq);
        }
        wrapper.page(page);

        wrapper = workDeptService.lambdaQuery()
                .eq(WorkDeptEntity::getCorpId, AccountService.getCorpId())
                .gt(WorkDeptEntity::getLevel, 0)
                .orderByAsc(WorkDeptEntity::getLevel);

        List<Integer> deptIdList = page.getRecords().stream().map(WorkDeptEntity::getId).collect(Collectors.toList());

        int size = deptIdList.size();
        for (int i = 0; i < size; i++) {
            wrapper.like(WorkDeptEntity::getPath, deptIdList.get(i));
            if (i != size - 1) {
                wrapper.or();
            }
        }
        List<WorkDeptEntity> deptEntityList = wrapper.list();
        voList.addAll(entityListToItemVoList(deptEntityList));
        return ApiRespUtils.transPage(page, voList);
    }

    private List<DeptPageItemVO> entityListToItemVoList(List<WorkDeptEntity> entityList) {
        Map<Integer, DeptPageItemVO> mapParent = new HashMap<>();
        Map<Integer, DeptPageItemVO> mapChild = new HashMap<>();
        int level = 1;
        int count = 1;
        List<DeptPageItemVO> voList = new ArrayList<>();
        for (WorkDeptEntity entity : entityList) {
            DeptPageItemVO vo = new DeptPageItemVO();

            entityToItemVo(entity, vo);

            if (entity.getLevel() == 1) {
                vo.setDepartmentPath("" + count++);
                voList.add(vo);
                mapParent.put(entity.getId(), vo);
            }

            if (entity.getLevel() - 1 > level) {
                mapParent.clear();
                mapParent.putAll(mapChild);
                mapChild.clear();
                level++;
            }

            if (entity.getLevel() > level) {
                mapChild.put(entity.getId(), vo);
            }

            if (entity.getLevel() > 1) {
                DeptPageItemVO voParent = mapParent.get(entity.getParentId());
                if (Objects.nonNull(voParent)) {
                    vo.setDepartmentPath(voParent.getDepartmentPath() + "-" + (voParent.getChildren().size() + 1));
                    voParent.getChildren().add(vo);
                }
            }
        }
        return voList;
    }

    private void entityToItemVo(WorkDeptEntity entity, DeptPageItemVO vo) {
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setLevel(getLevelName(entity.getLevel()));
        vo.setParentId(entity.getParentId());
        vo.setDepartmentId(entity.getId());
        vo.setChildren(new ArrayList<>());
    }

    private String getLevelName(int level) {
        switch (level) {
            case 1:
                return "一级部门";
            case 2:
                return "二级部门";
            case 3:
                return "三级部门";
            case 4:
                return "四级部门";
            case 5:
                return "五级部门";
            case 6:
                return "六级部门";
            case 7:
                return "七级部门";
            case 8:
                return "八级部门";
            case 9:
                return "九级部门";
            default:
                return "";
        }
    }

    @Override
    public List<WorkEmployeeDepartmentEntity> getDeptIdByEmpId(Integer id) {
        QueryWrapper<WorkEmployeeDepartmentEntity> workEmployeeDepartmentEntityQueryWrapper = new QueryWrapper();
        workEmployeeDepartmentEntityQueryWrapper.eq("employee_id",id);
        return this.baseMapper.selectList(workEmployeeDepartmentEntityQueryWrapper);
    }

    /**
     * @description:根据父级名称获取子级的部门id-排除父级本身
     * @author: Huayu
     * @time: 2021/2/9 16:08
     */
    private Map<String, Object> getDepartmentByName(Integer corpId, String name) {
        //模糊搜索部门数据
        String clStr = "id,path";
        List<WorkDeptEntity> workDeptEntityList = workDeptService.getWorkDepartmentsByCorpIdName(corpId, name, clStr);
        if (workDeptEntityList.size() == 0) {
            return null;
        }
        //根据部门集合中的path得到对应的子部门
        //获取集合的path并模糊查询出id存到集合中去并且去重得到全部部门id
        List<String> deptIds = new ArrayList();
        for (WorkDeptEntity workDeptEntity :
                workDeptEntityList) {
            List<WorkDeptEntity> workDept = workDeptService.getWorkDepartmentsByCorpIdPath(AccountService.getCorpId(), workDeptEntity.getPath(), "id");
            for (WorkDeptEntity dept :
                    workDept) {
                deptIds.add(dept.getId().toString());
            }
        }
        List<String> listWithoutDuplicates = deptIds.stream().distinct().collect(Collectors.toList());
        //查询数据
        return getWorkDepartment(listWithoutDuplicates);

    }

    private Map<String, Object> getWorkDepartment(List<String> listWithoutDuplicates) {
        String clStr = "id,name,path,level,parent_id";
        Map<String, Object> map = new HashMap();
        if (listWithoutDuplicates.size() == 0) {
            map.put("list", null);
            map.put("page", new PageModel(10, 0, 0));
            return map;
        }
        StringBuilder sb = new StringBuilder();
        for (String string :
                listWithoutDuplicates) {
            sb.append(string).append(",");
        }
        List<WorkDeptEntity> workDeptEntityList = workDeptService.getWorkDepartmentsBySearch(sb.substring(0, sb.length() - 1), clStr);
        if (workDeptEntityList.size() == 0) {
            return map;
        }
        Map<String, Object> map3 = handleWorkDepartmentData(map, workDeptEntityList);
        return handleWorkDepartmentData(map, workDeptEntityList);
    }

    private Map<String, Object> handleWorkDepartmentData(Map<String, Object> map, List<WorkDeptEntity> workDeptEntityList) {
        Map<String, Object> mapData = null;
        List<Map<String, Object>> mapDataList = new ArrayList();
        List<Map<String, Object>> mapDataChildList = new ArrayList();
        Map<String, Object> mapData1 = new HashMap();
        int level = 1;
        //处理子部门
        for (WorkDeptEntity workDept :
                workDeptEntityList) {
            if (workDept.getLevel() != 0) {
                //if(workDept.getLevel() == 1){
                //模糊查询path
                //level ++;
                List<WorkDeptEntity> workDeptEntityList1 = workDeptService.getWorkDepartmentsByPath(level, workDept.getPath());
                //递归遍历出子部门
                mapData = new HashMap();
                if (workDeptEntityList1.size() > 0) {
                    List<Map<String, Object>> mapChild = (List<Map<String, Object>>) recursive(workDeptEntityList1, level, mapDataChildList).get("childList");
                    for (Map map1 :
                            mapChild) {
                        mapData = map1;
                    }
                } else {
                    mapData.put("id", workDept.getId());
                    mapData.put("name", workDept.getName());
                    mapData.put("level", workDept.getLevel());
                    mapData.put("parentId", workDept.getParentId());
                    mapData.put("departmentId", workDept.getId());
                    mapData.put("departmentPath", level);
                }
                mapDataList.add(mapData);
                //}
            }
        }
        mapData1.put("list", mapDataList);
        return mapData1;
    }

    private Map<String, Object> recursive(List<WorkDeptEntity> workDeptEntityList, int level, List<Map<String, Object>> mapDataChildList) {
        Map<String, Object> mapDataChild1 = new HashMap();
        Map<String, Object> mapDataChild = null;
        List<Map<String, Object>> mapChildDataListMap = new ArrayList<Map<String, Object>>();
        List<WorkDeptEntity> mapChildDataList1 = new ArrayList<WorkDeptEntity>();
        //模糊查询path
        for (WorkDeptEntity workDeptEntity :
                workDeptEntityList) {
            mapDataChild = new HashMap();
            //父部门列表
            mapDataChild.put("id", workDeptEntity.getId());
            mapDataChild.put("name", workDeptEntity.getName());
            mapDataChild.put("level", workDeptEntity.getLevel());
            mapDataChild.put("parentId", workDeptEntity.getParentId());
            level++;
            //是否有子集
            List<WorkDeptEntity> workDeptEntityList2 = workDeptService.getWorkDepartmentsByPath(level, workDeptEntity.getPath());
            Map<String, Object> workDeptEntityMap = null;
            if (workDeptEntityList2.size() > 0) {
                Integer pathId = 1;
                //子部门列表
                for (WorkDeptEntity workDeptEntity2 :
                        workDeptEntityList2) {
                    if (workDeptEntity2.getPath().equals(workDeptEntity.getPath())) {
                        continue;
                    }
                    workDeptEntityMap = new HashMap<>();
                    WorkDeptEntity workDeptEntity1 = new WorkDeptEntity();
                    workDeptEntityMap.put("id", workDeptEntity2.getId());
                    workDeptEntityMap.put("name", workDeptEntity2.getName());
                    workDeptEntityMap.put("level", workDeptEntity2.getLevel());
                    workDeptEntityMap.put("parentId", workDeptEntity2.getParentId());
                    workDeptEntityMap.put("departmentId", workDeptEntity2.getId());
                    workDeptEntityMap.put("departmentPath", level + "-" + pathId);
                    workDeptEntity1.setId(workDeptEntity2.getId());
                    workDeptEntity1.setName(workDeptEntity2.getName());
                    workDeptEntity1.setLevel(workDeptEntity2.getLevel());
                    workDeptEntity1.setParentId(workDeptEntity2.getParentId());
                    workDeptEntity1.setPath(workDeptEntity2.getPath());
                    mapChildDataList1.add(workDeptEntity1);
                    mapChildDataListMap.add(workDeptEntityMap);
                    //把当前部门插入到子集
                    mapDataChild.put("children", mapChildDataListMap);
                    mapDataChildList.add(mapDataChild);
                    //mapDataChild = mapDataChild1;
                    return recursive(mapChildDataList1, level, (List<Map<String, Object>>) mapDataChildList.get(0).get("children"));

                }
                if (mapChildDataListMap.size() > 0) {
                    mapDataChild.put("children", mapChildDataListMap);
                    mapDataChildList.add(mapDataChild);
                    return recursive(mapChildDataList1, level, (List<Map<String, Object>>) mapDataChildList.get(0).get("children"));
                }
            } else {
                mapDataChild.clear();
                level = 1;
            }
        }
        mapDataChild1.put("childList", mapDataChildList);
        return mapDataChild1;
    }


}
