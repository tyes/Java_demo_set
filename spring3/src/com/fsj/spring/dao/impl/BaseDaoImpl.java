package com.fsj.spring.dao.impl;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.hibernate3.HibernateCallback;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;
import org.springframework.stereotype.Repository;

import com.fsj.spring.dao.BaseDao;
import com.fsj.spring.util.DataGridModel;

@Repository("baseDao")
@SuppressWarnings("rawtypes")
public class BaseDaoImpl extends HibernateDaoSupport implements BaseDao {

	private static final Logger log = LoggerFactory.getLogger(BaseDaoImpl.class);

	protected void initDao() {
	}

	public void saveOrUpdate(Object transientInstance) {
		log.debug("saving Object instance");
		try {
			//getHibernateTemplate().saveOrUpdate(transientInstance);
			getHibernateTemplate().merge(transientInstance);
			getSession().flush();
			log.debug("save successful");
		} catch (RuntimeException re) {
			log.error("save failed", re);
			throw re;
		}
	}

	public void delete(Object persistentInstance) {
		log.debug("deleting Object instance");
		try {
			getHibernateTemplate().delete(persistentInstance);
			log.debug("delete successful");
		} catch (RuntimeException re) {
			log.error("delete failed", re);
			throw re;
		}
	}

	public void deleteAll(Collection entities) {
		log.debug("deleting all Object instance");
		try {
			getHibernateTemplate().deleteAll(entities);
			log.debug("delete successful");
		} catch (RuntimeException re) {
			log.error("delete failed", re);
			throw re;
		}
	}

	public Object findById(Class clazz, Serializable id) {
		log.debug("getting Object instance with id: " + id);
		try {
			Object instance = (Object) getHibernateTemplate().get(clazz, id);
			return instance;
		} catch (RuntimeException re) {
			log.error("get failed", re);
			throw re;
		}
	}

	public List findByExample(Object instance) {
		log.debug("finding instance by example");
		try {
			List results = getHibernateTemplate().findByExample(instance);
			log.debug("find by example successful, result size: " + results.size());
			return results;
		} catch (RuntimeException re) {
			log.error("find by example failed", re);
			throw re;
		}
	}

	public List findByProperty(Class clazz, String propertyName, Object value) {
		log.debug("finding " + clazz.getSimpleName() + "instance with property: " + propertyName + ", value: " + value);
		try {
			String queryString = "from " + clazz.getName() + " as model where model." + propertyName + "= ?";
			return getHibernateTemplate().find(queryString, value);
		} catch (RuntimeException re) {
			log.error("find " + clazz.getSimpleName() + "by property name failed", re);
			throw re;
		}
	}

	public List findAll(Class clazz) {
		log.debug("finding " + clazz.getSimpleName() + " all instances");
		try {
			String queryString = "from " + clazz.getName();
			return getHibernateTemplate().find(queryString);
		} catch (RuntimeException re) {
			log.error("find " + clazz.getSimpleName() + " all failed", re);
			throw re;
		}
	}

	public List findByHQL(String hql, List pl) {
		log.debug("finding instance by hql");
		try {
			getHibernateTemplate().find(hql, pl.toArray());
		} catch (RuntimeException re) {
			log.error("find instance by hql failed", re);
			throw re;
		}
		return null;
	}

	public int updateBySQL(final String sql, final List pl) {
		log.debug("updating instance by sql");
		try {
			HibernateCallback callback = new HibernateCallback() {

				public Object doInHibernate(Session session) throws HibernateException, SQLException {
					SQLQuery query = session.createSQLQuery(sql);
					if (pl != null && !pl.isEmpty()) {
						for (int i = 0; i < pl.size(); i++) {
							query.setParameter(i, pl.get(i));
						}
					}
					return query.executeUpdate();
				}
			};

			return (Integer) getHibernateTemplate().execute(callback);
		} catch (RuntimeException re) {
			logger.error("update by sql failed.");
			throw re;
		}
	}

	public List findBySQl(final String sql, final List pl) {
		HibernateCallback callback = new HibernateCallback() {
			public Object doInHibernate(Session session) throws SQLException {
				List resultlist = new ArrayList();

				PreparedStatement preparedStatement = session.connection().prepareStatement(sql);
				if (pl != null && !pl.isEmpty()) {
					for (int i = 0; i < pl.size(); i++) {
						preparedStatement.setObject(i, pl.get(i));
					}
				}
				ResultSet resultSet = preparedStatement.executeQuery();
				ResultSetMetaData metaData = resultSet.getMetaData();
				int cols = metaData.getColumnCount();
				String[] columnName = new String[cols];
				for (int i = 0; i < columnName.length; i++) {
					columnName[i] = metaData.getColumnName(i + 1).toUpperCase();
				}
				while (resultSet.next()) {
					Map map = new HashMap();
					for (int i = 0; i < columnName.length; i++) {
						map.put(columnName[i], resultSet.getString(columnName[i]));
					}
					resultlist.add(map);
				}

				return resultlist;
			}
		};
		return (List) getHibernateTemplate().execute(callback);
	}
	
	public Map<String, Object> getPageListByExemple(DataGridModel dgm,Object instance) throws Exception{
		Map<String, Object> result = new HashMap<String, Object>(2);
		
		List totalList = getHibernateTemplate().findByExample(instance); 
		//这里使用了findByExample，如果没有外键关联（我的hibernate配置文件没有配置主外键对应关系），用这个可以简单很多，
		List pagelist = getHibernateTemplate().findByExample(instance, (dgm.getPage() - 1) * dgm.getRows(), dgm.getRows());
		
		result.put("total", totalList == null ? 0 : totalList.size());
		result.put("rows", pagelist);
		return result;
	}
	
	@SuppressWarnings("rawtypes")
	public Map<String, Object> getPageList(DataGridModel dgm,String countQuery,String resultQuery,Map<String,Object> params) throws Exception{
		
		Map<String, Object> result = new HashMap<String, Object>(2); 
		//String totalQuery = "select count(*) from TUser user,TDept dept where user.deptId=dept.id "; 
		//String fullQuery = "select new map(user as user,user.id as uid,dept.name as deptName) from TUser user,TDept dept where user.deptId=dept.id "; 
		//这里需要用new map()，jquery easyui datagrid有一个小bug，必须把idField单独列出来，要不然不能多选
		String orderString = "";
		if(StringUtils.isNotBlank(dgm.getSort()))
			orderString = " order by " + dgm.getSort() + " " + dgm.getOrder(); //排序
		//增加条件
		//StringBuffer sb = new StringBuffer();
		//Map<String,Object> params = new HashMap<String,Object>();
		/*
		if(user != null) {
			if(StringUtils.isNotBlank(user.getName())) {
				sb.append(" and user.name like :userName");
				params.put("userName", "%"+user.getName()+"%");
			}
			if(user.getAge() != null) {
				sb.append(" and user.age = :age");
				params.put("age", user.getAge());
			}
			if(user.getBirthday() != null) {
				sb.append(" and user.birthday = :birthday");
				params.put("birthday", user.getBirthday());
			}
			if(user.getDeptId() != null) {
				sb.append(" and dept.id = :deptId");
				params.put("deptId", user.getDeptId());
			}
		}
		*/
		
		//查询总数可以用getHibernateTemplate()，我下面用的是createQuery
		//Hibernate的动态条件查询用DetachedCriteria是一个比较好的解决
//			List totalList = getHibernateTemplate().findByNamedParam(countQuery, params.keySet().toArray(new String[0]), params.values().toArray());
//			int total = ((Long)totalList.iterator().next()).intValue();
		
		Query queryTotal = getSession().createQuery(countQuery);
		Query queryList = getSession().createQuery(resultQuery + orderString)
							.setFirstResult((dgm.getPage() - 1) * dgm.getRows()).setMaxResults(dgm.getRows());
		if(params!=null && !params.isEmpty()){
			Iterator<String> it = params.keySet().iterator();
			while(it.hasNext()){					
				String key = it.next();	
				queryTotal.setParameter(key, params.get(key));
				queryList.setParameter(key, params.get(key));
			}	
		}			
		int total = ((Long)queryTotal.uniqueResult()).intValue(); //这里必须先转成Long再取intValue，不能转成Integer
		
		List list = queryList.list();
		result.put("total", total);
		result.put("rows", list);
			
		return result;
	}
	// public static IDeptDao getFromApplicationContext(ApplicationContext ctx)
	// {
	// return (IDeptDao) ctx.getBean("TDeptDAO");
	// }

}