package org.jvnet.hudson.tools.versionnumber;

import hudson.Extension;
import hudson.Launcher;
import hudson.util.FormValidation;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * The version number is a format string 
 * <li>
 * <ul><b>BUILD_ID</b>: the number of the build</ul>
 * <ul><b>MONTHS_SINCE_START</b>: relying on the project start date, provides
 * the number of months since the start of the project</ul>
 * <ul><b>BUILDS_TODAY</b>: the number of builds today, including this one.</ul>
 * <ul><b>BUILDS_THIS_MONTH</b>: the number of builds this month.</ul>
 * <ul><b>BUILDS_THIS_YEAR</b>: the number of builds this year.</ul>
 * <p>
 *
 * @author Carl Lischeske - NETFLIX
 */
public class VersionNumberBuilder extends BuildWrapper {

	private static final DateFormat defaultDateFormat = new SimpleDateFormat("yyyy-MM-dd");
	
	private final String versionNumberString;
	private final Date projectStartDate;
	private final String environmentVariableName;
	
	private int buildsToday;
	private int buildsThisMonth;
	private int buildsThisYear;
	
    @DataBoundConstructor
    public VersionNumberBuilder(String versionNumberString,
    		String projectStartDate,
    		String environmentVariableName,
    		String buildsToday,
    		String buildsThisMonth,
    		String buildsThisYear) {
        this.versionNumberString = versionNumberString;
        this.projectStartDate = parseDate(projectStartDate);
        this.environmentVariableName = environmentVariableName;

        try {
        	this.buildsToday = Integer.parseInt(buildsToday);
        } catch (NumberFormatException nfe) {
        }
        try {
        	this.buildsThisMonth = Integer.parseInt(buildsThisMonth);
        } catch (NumberFormatException nfe) {
        }
        try {
        	this.buildsThisYear = Integer.parseInt(buildsThisYear);
        } catch (NumberFormatException nfe) {
        }
    }
    
    public int getBuildsToday() {
    	return buildsToday;
    }
    
    public int getBuildsThisMonth() {
    	return buildsThisMonth;
    }
    
    public int getBuildsThisYear() {
    	return buildsThisYear;
    }
    
    private static Date parseDate(String dateString) {
    	try {
    		return defaultDateFormat.parse(dateString);
    	} catch (Exception e) {
    		return new Date(0);
    	}
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getVersionNumberString() {
        return versionNumberString;
    }
    
    public String getProjectStartDate() {
    	return defaultDateFormat.format(projectStartDate);
    }
    public String getEnvironmentVariableName() {
    	return this.environmentVariableName;
    }
    
    @SuppressWarnings("unchecked")
    private void incBuild(Build build, PrintStream log) {
    	// get the values set in the previous build
    	Calendar curCal = build.getTimestamp();
    	Calendar todayCal = build.getPreviousBuild().getTimestamp();
    	
    	// increment builds per day
    	if (
    			curCal.get(Calendar.DAY_OF_MONTH) == todayCal.get(Calendar.DAY_OF_MONTH)
    			&& curCal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH)
    			&& curCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)
    		) {
    		buildsToday++;
    	} else {
    		buildsToday = 1;
    	}
    		
    	// increment builds per month
    	if (
    			curCal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH)
    			&& curCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)
    		) {
    		buildsThisMonth++;
    	} else {
    		buildsThisMonth = 1;
    	}
    	
    	// increment builds per year
    	if (
    			curCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)
    		) {
    		buildsThisYear++;
    	} else {
    		buildsThisYear = 1;
    	}
    }
    
    private static String formatVersionNumber(String versionNumberFormatString,
    		Date projectStartDate,
    		int buildsToday,
    		int buildsThisMonth,
    		int buildsThisYear,
    		Map<String, String> enVars,
    		Calendar buildDate,
    		PrintStream log) {
    	String vnf = new String(versionNumberFormatString);
    	
    	log.println("formatting " + vnf + ":");
    	
    	int expressionStart = 0;
    	do {
    		expressionStart = vnf.indexOf("${");
    		if (expressionStart > 0) {
    			int expressionEnd = vnf.indexOf("}", expressionStart);
    			int argumentStart = vnf.indexOf(",", expressionStart);
    			int argumentEnd = 0;
    			if (argumentStart > 0 && argumentStart < expressionEnd) {
        			argumentEnd = expressionEnd;
    				expressionEnd = argumentStart;
    			}
    			if (expressionEnd < expressionStart) {
    				vnf = vnf.substring(0, expressionStart);
    				continue;
    			}
    			String expressionKey = vnf.substring(expressionStart + 2, expressionEnd);
    			String argumentString = argumentEnd > 0 ? vnf.substring(argumentStart + 1, argumentEnd).trim() : "";
    			log.println("Found key: " + expressionKey);
    			log.println("Found argument: " + argumentString);
    			String replaceValue = "";
    			
    			// we have the expression key; if it's any known key, fill in the value
    			if ("".equals(expressionKey)) {
    				replaceValue = "";
    			} else if ("BUILD_DATE_FORMATTED".equals(expressionKey)) {
    				DateFormat fmt = SimpleDateFormat.getInstance();
    				if (!"".equals(argumentString)) {
    					// this next line is a bit tricky, but basically, we're looking returning everything
    					// inside a pair of quote marks; in other words, everything from after the first quote
    					// to before the second
    					String fmtString = argumentString.substring(argumentString.indexOf('"') + 1, argumentString.indexOf('"', argumentString.indexOf('"') + 1));
    					fmt = new SimpleDateFormat(fmtString);
    				}
    				replaceValue = fmt.format(buildDate.getTime());
    			} else if ("BUILD_DAY".equals(expressionKey)) {
    				replaceValue = sizeTo(Integer.toString(buildDate.get(Calendar.DAY_OF_MONTH)), argumentString.length());
    			} else if ("BUILD_MONTH".equals(expressionKey)) {
    				replaceValue = sizeTo(Integer.toString(buildDate.get(Calendar.MONTH) + 1), argumentString.length());
    			} else if ("BUILD_YEAR".equals(expressionKey)) {
    				replaceValue = sizeTo(Integer.toString(buildDate.get(Calendar.YEAR)), argumentString.length());
    			} else if ("BUILDS_TODAY".equals(expressionKey)) {
    				replaceValue = sizeTo(Integer.toString(buildsToday), argumentString.length());
    			} else if ("BUILDS_THIS_MONTH".equals(expressionKey)) {
    				replaceValue = sizeTo(Integer.toString(buildsThisMonth), argumentString.length());
    			} else if ("BUILDS_THIS_YEAR".equals(expressionKey)) {
    				replaceValue = sizeTo(Integer.toString(buildsThisYear), argumentString.length());
    			} else if ("MONTHS_SINCE_PROJECT_START".equals(expressionKey)) {
    				Calendar projectStartCal = Calendar.getInstance();
    				projectStartCal.setTime(projectStartDate);
    				int monthsSinceStart = buildDate.get(Calendar.MONTH) - projectStartCal.get(Calendar.MONTH);
    				monthsSinceStart += (buildDate.get(Calendar.YEAR) - projectStartCal.get(Calendar.YEAR)) * 12;
    				replaceValue = sizeTo(Integer.toString(monthsSinceStart), argumentString.length());
    			} else if ("YEARS_SINCE_PROJECT_START".equals(expressionKey)) {
    				Calendar projectStartCal = Calendar.getInstance();
    				projectStartCal.setTime(projectStartDate);
    				int yearsSinceStart = buildDate.get(Calendar.YEAR) - projectStartCal.get(Calendar.YEAR);
    				replaceValue = sizeTo(Integer.toString(yearsSinceStart), argumentString.length());
    			}
    			// if it's not one of the defined values, check the environment variables
    			else {
	    			for (String enVarKey : enVars.keySet()) {
	    				if (enVarKey.equals(expressionKey)) {
	    					replaceValue = enVars.get(enVarKey);
	    				}
	    			}
    			}
				vnf = vnf.replace("${" + expressionKey + "}", replaceValue);
    		}
    	} while (expressionStart > 0);
    	
    	return vnf;
    }

	private static String sizeTo(String s, int length) {
		while (s.length() < length) {
			s = "0" + s;
		}
		return s;
	}
    	
    @SuppressWarnings("unchecked")
	public Environment setUp(Build build, Launcher launcher, BuildListener listener) {
    	String formattedVersionNumber = "";
    	incBuild(build, listener.getLogger());
    	// we need to save here to persist the build increment we've just done
    	try {
    		build.getProject().save();
    	} catch (IOException ioe) {
    		build.setResult(Result.FAILURE);
    	}
    	try {
			formattedVersionNumber = formatVersionNumber(this.versionNumberString,
					this.projectStartDate,
					buildsToday,
					buildsThisMonth,
					buildsThisYear,
					build.getEnvironment(),
					build.getTimestamp(),
					listener.getLogger()
					);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		final String finalVersionNumber = formattedVersionNumber;
        return new Environment() {
        	@Override
        	public void buildEnvVars(Map<String, String> env) {
        		env.put(environmentVariableName, finalVersionNumber);
        	}
        };
    }

    @Override
    public BuildWrapperDescriptor getDescriptor() {
        // see Descriptor javadoc for more about what a descriptor is.
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link VersionNumberBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        public DescriptorImpl() {
            super(VersionNumberBuilder.class);
            load();
        }

        
        
        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This receives the current value of the field.
         */
        public FormValidation doCheckEnvironmentVariableName(@QueryParameter final String value) {
            if(value.length()==0)
                return FormValidation.error("Please set an environment variable name");
            else
                return FormValidation.ok();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This receives the current value of the field.
         */
        public FormValidation doCheckVersionNumberString(@QueryParameter final String value) {
            if(value.length()==0)
                return FormValidation.error("Please set a version number format string.  For more information, click on the ?.");
            else
            if(value.length()<4)
                return FormValidation.warning("Isn't the name too short?");
            else
                return FormValidation.ok();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This receives the current value of the field.
         */
        public FormValidation doCheckProjectStartDate(@QueryParameter final String value)  {
             if (value.length() > 0 && parseDate(value).compareTo(new Date(0)) == 0) {
                return FormValidation.error("Valid dates are in the format yyyy-mm-dd");
             } else {
                return FormValidation.ok();
             }
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Create a formatted version number";
        }

        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            return super.configure(req, json);
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> proj) {
        	return true;
        }
    }
}

